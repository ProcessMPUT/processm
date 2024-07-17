package processm.miners.causalnet.heuristicminer

import org.jgrapht.alg.clique.PivotBronKerboschCliqueFinder
import org.jgrapht.graph.DefaultUndirectedGraph
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.*
import processm.core.models.metadata.BasicMetadata
import processm.core.models.metadata.DefaultMetadataProvider
import processm.core.models.metadata.MetadataSubject
import processm.core.models.metadata.SingleDoubleMetadata
import processm.helpers.DoubleArray2D
import processm.helpers.map2d.DoublingMap2D
import processm.helpers.mapToSet
import processm.miners.causalnet.CausalNetMiner


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
 * @param dependencyThreshold The threshold on the dependency measure between two different nodes that do not form 2-loop. All arcs with the dependency greater or equal than the specified threshold are added to the model. Some other arcs may be added as well to ensure every node (except start and end) has at least one incoming and at least one outgoing arc. See Equation 1 in the paper.
 * @param l1Threshold The threshold on the dependency measure between a node and itself, attaining it results in adding a 1-loop to the model. See Equation 2 in the paper.
 * @param l2Threshold The threshold on the dependency measure between two nodes such that attaining it results in adding a 2-loop between them to the model. See Equation 3 in the paper.
 * @param andThreshold The threshold on the AND-dependency measure between two arcs, used while distinguishing between AND and XOR relation. See Section 2.2 in the paper.
 */
class OriginalHeuristicMiner(
    val dependencyThreshold: Double = .5,
    val l1Threshold: Double = dependencyThreshold,
    val l2Threshold: Double = dependencyThreshold,
    val andThreshold: Double = .65
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

        private const val SILENT_START_NAME = "4f9cd754start"
        private const val SILENT_START = 0
        private const val SILENT_END_NAME = "4f9cd754end"
        private const val SILENT_END = SILENT_START + 1
        private const val FIRST_REAL_NODE = SILENT_END + 1
    }

    override lateinit var result: MutableCausalNet
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

    private fun computeFrequencies(log: Log) {
        event2index.clear()
        event2index[SILENT_START_NAME] = SILENT_START
        event2index[SILENT_END_NAME] = SILENT_END
        freq1.clear()
        freq2.clear()
        for (trace in log.traces) {
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
            }
            freq1.compute(prev1, SILENT_END) { _, _, v -> (v ?: 0) + 1 }
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
        return outgoing
    }

    /**
     * Compute the set of bindings for the node [a] with its incoming/outgoing dependencies given in [edgesSet]
     * (incoming edges for joins, outgoing edges for splits).
     *
     * The procedure consists of two steps:
     * 1. Determining pairwise AND-relations between any two dependencies in [edgesSet]. The paper gives a heuristic
     * defined by Equation 4 for that and the code follows.
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
    private fun computeBindings(a: Int, edgesSet: HashSet<Int>): Iterable<Set<Int>> {
        if (edgesSet.isEmpty()) return emptyList()
        val edges = edgesSet.toIntArray()
        val conjunctsGraph = DefaultUndirectedGraph<Int, Int>(Int::class.java)
        edges.forEach(conjunctsGraph::addVertex)
        var nextEdgeId = 0
        for (i in edges.indices) {
            val b = edges[i]
            for (j in i + 1 until edges.size) {
                val c = edges[j]
                // Equation 4
                val v = ((freq1[b, c]?.toDouble() ?: 0.0) + (freq1[c, b]?.toDouble()
                    ?: 0.0)) / ((freq1[a, b]?.toDouble() ?: 0.0) + (freq1[a, c]?.toDouble() ?: 0.0) + 1)
                if (v >= andThreshold) {
                    conjunctsGraph.addEdge(c, b, nextEdgeId++)
                }
            }
        }
        return PivotBronKerboschCliqueFinder(conjunctsGraph)
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
            ?.let { possibleStart -> if (incoming[possibleStart].singleOrNull() == SILENT_START) possibleStart else null }
            ?: SILENT_START
        val end = incoming[SILENT_END].singleOrNull()
            ?.let { possibleEnd -> if (outgoing[possibleEnd].singleOrNull() == SILENT_END) possibleEnd else null }
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
            for (s in 0 until nNodes) {
                for (split in computeBindings(s, outgoing[s])) {
                    addSplit(Split(split.mapToSet { t -> Dependency(index2node[s], index2node[t]) }))
                }
            }
            for (t in 0 until nNodes) {
                for (join in computeBindings(t, incoming[t]))
                    addJoin(Join(join.mapToSet { s -> Dependency(index2node[s], index2node[t]) }))
            }
            addMetadataProvider(DefaultMetadataProvider(BasicMetadata.DEPENDENCY_MEASURE, dependencyMetadata))
        }
    }

}