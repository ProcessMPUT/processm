package processm.miners.causalnet.heuristicminer

import org.jgrapht.alg.clique.PivotBronKerboschCliqueFinder
import org.jgrapht.graph.DefaultUndirectedGraph
import processm.conformance.models.alignments.CompositeAligner
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.*
import processm.core.models.metadata.BasicMetadata
import processm.core.models.metadata.DefaultMetadataProvider
import processm.core.models.metadata.MetadataSubject
import processm.core.models.metadata.SingleDoubleMetadata
import processm.helpers.*
import processm.helpers.map2d.DoublingMap2D
import processm.miners.causalnet.CausalNetMiner
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min


/**
 * As described in Weijters, A. J. M. M., Aalst, van der, W. M. P., & Alves De Medeiros, A. K. (2006). Process mining with the
 * HeuristicsMiner algorithm. (BETA publicatie : working papers; Vol. 166). Technische Universiteit Eindhoven.
 * https://pure.tue.nl/ws/portalfiles/portal/2388011/615595.pdf
 *
 * It exposes support for dependencies as [BasicMetadata.DEPENDENCY_MEASURE]. The actual values of dependency are not
 * exposed this way, as they come from four different sources and aggregating them would be confusing.
 *
 * The default values of the parameters were set to mimic the function `discover_petri_net_heuristics` from `pm4py`
 *
 * Contrary to the name, this is no longer the original heuristic miner. The heuristics for bindings was replaced with something more robust and auto-tuning for its threshold was added.
 *
 * @param dependencyThreshold The threshold on the dependency measure between two different nodes that do not form 2-loop. All arcs with the dependency greater or equal than the specified threshold are added to the model. Some other arcs may be added as well to ensure every node (except start and end) has at least one incoming and at least one outgoing arc. See Equation 1 in the paper.
 * @param l1Threshold The threshold on the dependency measure between a node and itself, attaining it results in adding a 1-loop to the model. See Equation 2 in the paper.
 * @param l2Threshold The threshold on the dependency measure between two nodes such that attaining it results in adding a 2-loop between them to the model. See Equation 3 in the paper.
 * @param andThreshold The threshold on the AND-dependency measure between two arcs, used while distinguishing between AND and XOR relation. See Section 2.2 in the paper. Note a different heuristic is applied and thus the measure is bound to the range 0..1, hence, setting the threshold above 1 guarantees all bindings will be unary.
 * @param autotuneAndThreshold If true, search for a suitable value of for the and threshold is performed in the range [andThreshold]..1. The goal of the search is to find the lowest value such that an empty trace can be aligned with the resulting model within the time limit specified by [alignerTimeout] and [alignerTimeoutUnit]
 * @param pool The thread pool to be used by the [CompositeAligner] while auto-tuning; ignored if [autotuneAndThreshold]=false
 * @param alignerTimeout The timeout for computing a single alignment while auto-tuning; ignored if [autotuneAndThreshold]=false
 * @param alignerTimeoutUnit The time unit for [alignerTimeout]
 */
class OriginalHeuristicMiner(
    val dependencyThreshold: Double = .5,
    val l1Threshold: Double = dependencyThreshold,
    val l2Threshold: Double = dependencyThreshold,
    val andThreshold: Double = .65,
    val autotuneAndThreshold: Boolean = true,
    val pool: ExecutorService = Executors.newCachedThreadPool(),
    val alignerTimeout: Long = 1,
    val alignerTimeoutUnit: TimeUnit = TimeUnit.SECONDS
) : CausalNetMiner {

    companion object {
        /**
         * Returns the pair (i, j) such that $i \in x$, $j \in y$ and $f(i, j) >= f(a, b) \forall a\in x, b\in y$
         */
        fun argmax(x: Iterable<Int>, y: Iterable<Int>, f: (Int, Int) -> Double): Pair<Int, Int> {
            var bestx: Int? = null
            var besty: Int? = null
            var best: Double? = null
            for (i in x)
                for (j in y) {
                    val v = f(i, j)
                    if (best == null || v > best) {
                        best = v
                        bestx = i
                        besty = j
                    }
                }
            checkNotNull(bestx)
            checkNotNull(besty)
            return bestx to besty
        }


        /**
         * Detects connected components by first labeling each node with a different number to each node and then traversing
         * the graph and updating the labels with the minimum of its own number and the number of all successors.
         * The updating is repeated as long as any changes are made.
         */
        internal fun detectConnectedComponents(nNodes: Int, outgoing: Array<HashSet<Int>>): IntArray {
            val clusters = IntArray(nNodes) { it }
            val queue = LinkedList<Int>()
            queue.addAll(0 until nNodes)
            while (queue.isNotEmpty()) {
                val node = queue.pop()
                if (outgoing[node].isNotEmpty()) {
                    val minCluster = min(outgoing[node].minOf { clusters[it] }, clusters[node])
                    if (minCluster != clusters[node])
                        clusters[node] = minCluster
                    for (o in outgoing[node]) {
                        if (clusters[o] != minCluster) {
                            clusters[o] = minCluster
                            queue.add(o)
                        }
                    }
                }
            }
            return clusters
        }

        private const val SILENT_START_NAME =
            "star\u0000t\u0000\u0000\u0000\u0000" // null should not occur in normal activity name
        private const val SILENT_START = 0
        private const val SILENT_END_NAME =
            "en\u0000d\u0000\u0000\u0000\u0000" // null should not occur in normal activity name
        private const val SILENT_END = SILENT_START + 1
        private const val FIRST_REAL_NODE = SILENT_END + 1
    }

    override lateinit var result: MutableCausalNet
        private set

    /**
     * The value for [andThreshold] the model in [result] was computed with.
     * If [autotuneAndThreshold] is false, [modelAndThreshold] == [andThreshold], else [modelAndThreshold] >= [andThreshold]
     *
     * This value can be passed as [andThreshold] to [OriginalHeuristicMiner] with [autotuneAndThreshold] set to false to produce the same model
     */
    var modelAndThreshold: Double = Double.NaN
        private set
    private val event2index = HashMap<String, Int>()

    private val nNodes: Int
        get() = event2index.size

    /**
     * # of occurrences of notation 1 page 6
     */
    private val freq1 = DoublingMap2D<Int, Int, Int>()

    /**
     * # of occurrences of notation 5 page 6
     */
    private val freq2 = DoublingMap2D<Int, Int, Int>()

    /**
     * Normalized sum over traces of the following ratio:
     * # of non-overlapping triples a..b..c
     * ------------------------------------
     * max(# of non-overlapping pairs a..b, # of non-overlapping pairs a..c)
     * where a, b, c are, respectively, the first, second and third element of the key
     * The sum is normalized by dividing by the number of traces containing a, b, c in any order and number
     *
     * [MathJax rendering of the formula](https://saxarona.github.io/mathjax-viewer/?input=%5Cfrac%7B%5Csum_t%5Cfrac%7B%7C%5C%7B%28i%2Cj%2Ck%29%3A+i%5Clt+j+%5Clt+k+%5Cland+t_i%3Da+%5Cland+t_j%3Db+%5Cland+t_k%3Dc%5C%7D%7C%7D%7B%5Cmax%5C%7B%7C%5C%7B%28i%2Cj%29%3A+i%5Clt+j+%5Cland+t_i%3Da+%5Cland+t_j%3Db%5C%7D%7C%2C+%7C%5C%7B%28i%2Ck%29%3A+i%5Clt+j+%5Clt+k+%5Cland+t_i%3Da+%5Cland+t_k%3Dc%5C%7D%7C%5C%7D%7D%7D%7B%7C%5C%7Bt%3A+a%5Cin+t+%5Cland+b%5Cin+t+%5Cland+c+%5Cin+t%5C%7D%7C%7D)
     */
    private val a_bc = HashMap<Triple<Int, Int, Int>, Double>()

    /**
     * Normalized sum over traces of the following ratio:
     * # of non-overlapping triples a..b..c
     * ------------------------------------
     * max(# of non-overlapping pairs a..c, # of non-overlapping pairs b..c)
     * where c, a, b are, respectively, the first, second and third element of the key
     * The sum is normalized by dividing by the number of traces containing a, b, c in any order and number
     */
    private val c_ab = HashMap<Triple<Int, Int, Int>, Double>()


    /**
     * [freq1] and [freq2] are computed according to the paper; [a_bc] and [c_ab] are computed using the following algorithm:
     * We assume that the only relevant attribute of an event is its name, and thus there is a 1:1 mapping between events and nodes.
     * For every trace in [log], we iterate it from the start to the end. Denote the current event by e.
     * `singletons[e]` is increased by 1 (i.e., this is the number of times the event e was observed so far).
     * All nodes visited previously (stored in pastIdxs) are visited, and for every node p, if the number of visits so far
     * (`singletons[p]`) is at least the number of visits to the current node (`singletons[e]`), it means that it is possible
     * that p is the cause of e; thus, `pairs[p, e]` is increased by one, and:
     *
     * 1. All other pairs of form (p, f) (where e!=f) are considered; if pairs[p, f]>=pairs[p, e], it is possible that p
     * jointly causes e and f; thus `a_bc_trace[(p, e', f')]` is increased (where e'=min(e, f), f'=max(e, f), to ensure we
     * are not relying on the relative order of e and f in the trace.
     * 2. All other pairs of form (r, e) are considered (r!=p); if pairs[r, e]>=pairs[p, e], it is possible that p and r
     * are jointly the cause of e; thus, `c_ab_trace[(e, p', r')]` is increased (again, p' and r' represent, respectively,
     * min(p, r) and max(p, r))
     *
     * After iterating over the trace concludes, `a_bc_trace` (resp. `c_ab_trace`) are used to update [a_bc] (resp. [c_ab]),
     * to arrive at the (for now unnormalized) sum described in the doc of [a_bc] (resp. [c_ab]).
     *
     * The set of events present in the tracec (pastIdxs) is also used to update `triples`, which for any triple (a,b,c) counts the
     * number of traces containing a, b, c at least once, independently of their relative order. To ensure order
     * is disregarded, the keys for `triples` are sorted such that always a < b < c.
     *
     * After the log concludes, `triples` is used to normalize [a_bc] and [c_ab], effectively computing the average
     * of the per-trace scores.
     */
    private fun computeFrequencies(log: Log) {
        a_bc.clear()
        c_ab.clear()
        event2index.clear()
        event2index[SILENT_START_NAME] = SILENT_START
        event2index[SILENT_END_NAME] = SILENT_END
        freq1.clear()
        freq2.clear()
        val singletons = Counter<Int>() // per trace
        val pairs = DoublingMap2D<Int, Int, Int>()  // per trace
        val triples = Counter<Triple<Int, Int, Int>>() // per log!
        val c_ab_trace = Counter<Triple<Int, Int, Int>>() // per trace
        val a_bc_trace = Counter<Triple<Int, Int, Int>>() // per trace
        val pastIdxs = HashSet<Int>() // per trace; different nodes visited so far
        for (trace in log.traces) {
            singletons.clear()
            pairs.clear()
            a_bc_trace.clear()
            c_ab_trace.clear()
            pastIdxs.clear()
            var prev1: Int = SILENT_START
            var prev2: Int? = null
            for (event in trace.events) {
                val name = event.conceptName ?: continue
                val idx = event2index.computeIfAbsent(name) { event2index.size }
                assert(idx >= FIRST_REAL_NODE)
                freq1.compute(prev1, idx) { _, _, v -> (v ?: 0) + 1 }
                if (prev2 == idx)
                    freq2.compute(prev1, idx) { _, _, v -> (v ?: 0) + 1 }
                prev2 = prev1
                prev1 = idx

                singletons.inc(idx)
                for (pastIdx in pastIdxs) {
                    if (singletons[pastIdx] >= singletons[idx]) {
                        val m = pairs.compute(pastIdx, idx) { _, _, v ->
                            (v ?: 0) + 1
                        }!!
                        for ((col, v) in pairs.getRow(pastIdx)) {
                            if (col != idx && v >= m) {
                                val b = min(col, idx)
                                val c = max(col, idx)
                                a_bc_trace.inc(Triple(pastIdx, b, c))
                            }
                        }
                        for ((row, v) in pairs.getColumn(idx)) {
                            if (row != pastIdx && v >= m) {
                                val c = idx
                                val a = min(row, pastIdx)
                                val b = max(row, pastIdx)
                                c_ab_trace.inc(Triple(c, a, b))
                            }
                        }
                    }
                }
                pastIdxs.add(idx)
            }
            freq1.compute(prev1, SILENT_END) { _, _, v -> (v ?: 0) + 1 }
            for ((abc, tripleFreq) in a_bc_trace) {
                assert(tripleFreq >= 1)
                val a = abc.first
                val b = abc.second
                val c = abc.third
                assert(b < c)
                val abFreq = pairs[a, b] ?: 0
                val acFreq = pairs[a, c] ?: 0
                assert(abFreq >= tripleFreq)
                assert(acFreq >= tripleFreq)
                val coeff = tripleFreq.toDouble() / max(abFreq, acFreq)
                assert(coeff in 0.0..1.0)
                a_bc.compute(abc) { _, old ->
                    (old ?: 0.0) + coeff
                }
            }
            for ((cab, tripleFreq) in c_ab_trace) {
                assert(tripleFreq >= 1)
                val c = cab.first
                val a = cab.second
                val b = cab.third
                assert(a < b)
                val acFreq = pairs[a, c] ?: 0
                val bcFreq = pairs[b, c] ?: 0
                assert(acFreq >= tripleFreq)
                assert(bcFreq >= tripleFreq)
                val coeff = tripleFreq.toDouble() / max(acFreq, bcFreq)
                assert(coeff in 0.0..1.0)
                c_ab.compute(cab) { _, old ->
                    (old ?: 0.0) + coeff
                }
            }
            val pastIdxsSorted = pastIdxs.sorted()
            for (i in pastIdxsSorted.indices) {
                val a = pastIdxsSorted[i]
                for (j in i + 1 until pastIdxsSorted.size) {
                    val b = pastIdxsSorted[j]
                    for (k in j + 1 until pastIdxsSorted.size) {
                        val c = pastIdxsSorted[k]
                        triples.inc(Triple(a, b, c))
                    }
                }
            }
        }
        for (k in a_bc.keys)
            a_bc.computeIfPresent(k) { k, v ->
                val k2 = k.sort()
                v / triples[k2]
            }
        for (k in c_ab.keys)
            c_ab.computeIfPresent(k) { k, v ->
                val k2 = k.sort()
                v / triples[k2]
            }
    }

    private fun computeDependency(): DoubleArray2D {
        val dependency = DoubleArray2D(nNodes, nNodes)
        for (i in 0 until nNodes) {
            for (j in i + 1 until nNodes) {
                val a = freq1[i, j]?.toDouble() ?: 0.0
                val b = freq1[j, i]?.toDouble() ?: 0.0
                // Equation 1
                dependency[i, j] = (a - b) / (a + b + 1)
                dependency[j, i] = -dependency[i, j]
            }
        }
        return dependency
    }

    /**
     * Returns a set of all nodes that are in 1-loops. A set because there's membership-checking later on.
     * The detection is based on Equation 2 of the paper.
     */
    private fun detectL1(): Set<Int> {
        val result = HashSet<Int>()
        for (i in 0 until nNodes) {
            val v = freq1[i, i]?.toDouble()?.let { it / (it + 1) } ?: 0.0
            if (v >= l1Threshold)
                result.add(i)
        }
        return result
    }

    /**
     * Returns a list of all 2-loops. The detection is based on Equation 3 of the paper.
     * If both nodes have 1-loops, the pair is rejected, as the log is sufficiently explained by the 1-loops.
     */
    private fun detectL2(l1: Set<Int>): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>()
        for (i in 0 until nNodes) {
            for (j in i + 1 until nNodes) {
                val a = freq2[i, j]?.toDouble() ?: 0.0
                val b = freq2[j, i]?.toDouble() ?: 0.0
                // Equation 3
                val v = (a + b) / (a + b + 1)
                if (v >= l2Threshold && !(i in l1 && j in l1)) {
                    result.add(i to j)
                }
            }
        }
        return result
    }

    /**
     * Add to [outgoing] arcs from nodes of [xs] to [ys].
     *
     * * No arcs are added if all the values of [dependency] from [xs] to [ys] are non-positive
     * * Otherwise:
     *     * The arc with the maximal value of [dependency] is added if [allConnectedHeuristics]=`true`. Ties are broken arbitrarily.
     *     * All arcs with the value of [dependency] of at least [dependencyThreshold] are added.
     */
    private fun addArcs(
        dependency: DoubleArray2D,
        outgoing: Array<HashSet<Int>>,
        xs: Iterable<Int>,
        ys: Iterable<Int>,
        allConnectedHeuristics: Boolean
    ) {
        val (x, y) = argmax(xs, ys, dependency::get)
        if (dependency[x, y] <= 0)
            return
        if (allConnectedHeuristics)
            outgoing[x].add(y)
        if (dependency[x, y] >= dependencyThreshold) {
            for (i in xs)
                for (j in ys)
                    if (dependency[i, j] >= dependencyThreshold)
                        outgoing[i].add(j)
        }
    }


    /**
     * Construct the dependency graph. Include:
     * * all 1-loops from [detectL1]
     * * all 2-loops from [detectL2]
     * * all arcs added by [addArcs], assuming that 2-loops are a single cluster and thus applying the all-activies-connected is unnecessary for one of the pair
     */
    private fun computeGraph(): Array<HashSet<Int>> {
        val dependency = computeDependency()
        val outgoing = Array(nNodes) { HashSet<Int>() }
        //1. Detect self-loops
        val l1 = detectL1()
        for (i in l1)
            outgoing[i].add(i)
        //2. Detect 2-loops
        val l2 = detectL2(l1)
        for ((i, j) in l2) {
            outgoing[i].add(j)
            outgoing[j].add(i)
        }
        //3. All-activities-connected
        for (i in 0 until nNodes) {
            val alreadyConnected =
                l2.any { (it.first == i && outgoing[it.second].size > 1) || (it.second == i && outgoing[it.first].size > 1) }
            val current = listOf(i)
            //3a. Outgoing
            addArcs(dependency, outgoing, current, 0 until nNodes, !alreadyConnected)
            //3b. Incoming
            addArcs(dependency, outgoing, 0 until nNodes, current, !alreadyConnected)
        }
        // fix dangling nodes, i.e., nodes that have no outgoing edges or no incoming edges, excl. SILENT_START and SILENT_END
        val incoming = outgoing.flatMapTo(HashSet()) { it }
        for (i in FIRST_REAL_NODE until nNodes) {
            if (outgoing[i].isEmpty())
                outgoing[i].add(SILENT_END)
            if (i !in incoming)
                outgoing[SILENT_START].add(i)
        }
        // fix disconnected components - it may happen the graph has more than one connected components
        with(detectConnectedComponents(nNodes, outgoing)) {
            val startCluster = this[SILENT_START]
            val visited = mutableSetOf(startCluster)
            // every cluster different than the cluster containing SILENT_START must be connected by at least one arc with SILENT_START
            for (n in 0 until nNodes) {
                if (this[n] !in visited) {
                    outgoing[SILENT_START].add(n)
                    visited.add(this[n])
                }
            }
        }
        return outgoing
    }

    /**
     * Compute the set of bindings for the node [a] with its incoming/outgoing dependencies given in [edgesSet]
     * (incoming edges for joins, outgoing edges for splits).
     *
     * The procedure consists of two steps:
     * 1. Determining pairwise AND-relations between any two dependencies in [edgesSet]. The paper gives a heuristic
     * defined by Equation 4 for that and the code follows, except that the heuristics is applied only to activities
     * that are parallel (i.e., there's no edge between them)
     * 2. Aggregating the information. The paper seems to apply some heuristic algorithm that is not precisly defined.
     * I think the authors did not realize they are facing the maximal clique problem in a graph represented by the AND-relation.
     * The implementation uses the pivoting version of the Bron-Kerbosch algorithm from [PivotBronKerboschCliqueFinder].
     *
     * Apparently, there are three variants of the algorithm (1). It seems to me the graph is likely to have many non-maximal
     * cliques, hence the pivoting variant seems to be preferred. Conversely, the graph is not necessarily sparse, hence
     * the variant with ordering is probably not suitable. Nevertheless, no performance comparison was performed.
     *
     * 1. https://en.wikipedia.org/w/index.php?title=Bron%E2%80%93Kerbosch_algorithm&oldid=1209826174
     */
    private fun computeBindings(
        a: Int,
        edgesSet: HashSet<Int>,
        source: Map<Triple<Int, Int, Int>, Double>,
        graph: Array<HashSet<Int>>,
        threshold: Double
    ): Iterable<Set<Int>> {
        if (edgesSet.isEmpty()) return emptyList()
        val edges = edgesSet.toIntArray()
        val conjunctsGraph = DefaultUndirectedGraph<Int, Int>(Int::class.java)
        edges.forEach(conjunctsGraph::addVertex)
        var nextEdgeId = 0
        for (i in edges.indices) {
            val b = edges[i]
            if (b == a)
                continue
            for (j in i + 1 until edges.size) {
                val c = edges[j]
                if (c == a)
                    continue
                if (c in graph[b] || b in graph[c])
                    continue
                val v = (source[Triple(a, b, c)] ?: 0.0) + (source[Triple(a, c, b)] ?: 0.0)
                assert(v in 0.0..1.0)
                if (v >= threshold) {
                    conjunctsGraph.addEdge(c, b, nextEdgeId++)
                }
            }
        }
        return PivotBronKerboschCliqueFinder(conjunctsGraph)
    }

    private fun MutableCausalNet.computeAllBindings(
        outgoing: Array<HashSet<Int>>,
        incoming: Array<HashSet<Int>>,
        index2node: Array<Node>,
        threshold: Double
    ) {
        clearBindings()
        for (s in 0 until nNodes) {
            for (split in computeBindings(s, outgoing[s], source = a_bc, outgoing, threshold)) {
                addSplit(Split(split.mapToSet { t -> Dependency(index2node[s], index2node[t]) }))
            }
        }
        for (t in 0 until nNodes) {
            for (join in computeBindings(t, incoming[t], source = c_ab, outgoing, threshold))
                addJoin(Join(join.mapToSet { s -> Dependency(index2node[s], index2node[t]) }))
        }
    }

    override fun processLog(log: Log) {
        computeFrequencies(log)
        val outgoing = computeGraph()
        val incoming = Array(nNodes) { HashSet<Int>() }
        for (s in 0 until nNodes)
            for (t in outgoing[s])
                incoming[t].add(s)
        val index2node = arrayOfNulls<Node>(nNodes).apply {
            for ((e, i) in event2index) {
                assert(i in 0 until nNodes)
                assert(this[i] === null)
                this[i] = when (i) {
                    SILENT_START -> Node("start", isSilent = true)
                    SILENT_END -> Node("end", isSilent = true)
                    else -> Node(e)
                }
            }
        } as Array<Node> // cast, because nulls are no longer possible in the array
        val start = outgoing[SILENT_START].singleOrNull()
            ?.let { possibleStart -> if (incoming[possibleStart].singleOrNull() == SILENT_START && possibleStart != SILENT_END) possibleStart else null }
            ?: SILENT_START
        val end = incoming[SILENT_END].singleOrNull()
            ?.let { possibleEnd -> if (outgoing[possibleEnd].singleOrNull() == SILENT_END && possibleEnd != SILENT_START) possibleEnd else null }
            ?: SILENT_END
        if (start != SILENT_START) {
            outgoing[SILENT_START].clear()
            for (n in 0 until nNodes)
                incoming[n].remove(SILENT_START)
        }
        if (end != SILENT_END) {
            incoming[SILENT_END].clear()
            for (n in 0 until nNodes)
                outgoing[n].remove(SILENT_END)
        }
        result = MutableCausalNet(
            start = index2node[start],
            end = index2node[end]
        ).apply {
            addInstance(index2node[start])
            addInstance(index2node[end])
            for (n in FIRST_REAL_NODE until nNodes)
                addInstance(index2node[n])
            val dependencyMetadata = HashMap<MetadataSubject, SingleDoubleMetadata>()
            for (s in 0 until nNodes)
                for (t in outgoing[s]) {
                    val d = addDependency(index2node[s], index2node[t])
                    freq1[s, t]?.toDouble()?.let { dependencyMetadata[d] = SingleDoubleMetadata(it) }
                }
            if (autotuneAndThreshold) {
                modelAndThreshold = stepArgMin(andThreshold, 1.01) { threshold ->
                    computeAllBindings(outgoing, incoming, index2node, threshold)
                    val success = runCatching {
                        CompositeAligner(this, pool = pool).align(
                            Trace(emptySequence()),
                            timeout = alignerTimeout,
                            unit = alignerTimeoutUnit
                        )
                    }.isSuccess
                    if (success) threshold else {
                        clearBindings()
                        null
                    }
                }
                if (splits.isEmpty())
                    computeAllBindings(outgoing, incoming, index2node, modelAndThreshold)
            } else {
                modelAndThreshold = andThreshold
                computeAllBindings(outgoing, incoming, index2node, andThreshold)
            }
            addMetadataProvider(DefaultMetadataProvider(BasicMetadata.DEPENDENCY_MEASURE, dependencyMetadata))
        }
    }

}
