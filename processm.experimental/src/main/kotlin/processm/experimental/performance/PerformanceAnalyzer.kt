package processm.experimental.performance

import org.apache.commons.lang3.math.Fraction
import processm.core.helpers.Counter
import processm.core.helpers.HierarchicalIterable
import processm.core.helpers.mapToSet
import processm.core.log.Event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.models.causalnet.*
import processm.core.models.commons.Activity
import processm.core.verifiers.causalnet.CausalNetVerifierImpl
import processm.miners.heuristicminer.HashMapWithDefault
import processm.miners.heuristicminer.bindingproviders.LazyCausalNetState
import processm.miners.heuristicminer.windowing.HasFeatures
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.max
import kotlin.math.min

data class StateContext(
    val model: CausalNet,
    val cache: HashMap<Node, Pair<Map<Dependency, Long>, List<Pair<Join, Long>>>>
)

open class CausalNetStateWithJoins : CausalNetStateImpl {
    val context: StateContext
    val activeJoins: Set<Join?>
        get() = joins
    private var joins: HashSet<Join?>
    private val activeDepsEnc = HashMap<Node, Long>()

    constructor(context: StateContext) : super() {
        this.context = context
        this.joins = hashSetOf(null)
    }

    constructor(stateBefore: CausalNetStateWithJoins) : super(stateBefore) {
        this.context = stateBefore.context
        this.joins = stateBefore.joins
        this.activeDepsEnc.putAll(stateBefore.activeDepsEnc)
    }

    private fun executeJoin(join: Join?): List<Dependency> {
        if (join != null) {
            val removedDeps = ArrayList<Dependency>()
            //    check(this.containsAll(join.dependencies)) { "It is impossible to execute this join in the current state" }
            for (d in join.dependencies) {
                val oldCtr = this.remove(d, 1)
                if (oldCtr == 1)
                    removedDeps.add(d)
            }
            return removedDeps
        } else
            return emptyList()
    }

    private fun executeSplit(split: Split?): List<Dependency> {
        if (split != null) {
            val addedDeps = ArrayList<Dependency>()
            for (dep in split.dependencies) {
                val oldCtr = this.add(dep, 1)
                if (oldCtr == 0)
                    addedDeps.add(dep)
            }
            return addedDeps
        } else
            return emptyList()
    }

    private fun retrieve(target: Node): Pair<Map<Dependency, Long>, List<Pair<Join, Long>>> =
        context.cache.computeIfAbsent(target) {
            var ctr = 0
            val d2b = context.model.incoming[target].orEmpty().associateWith { 1L shl (ctr++) }
            check(ctr <= Long.SIZE_BITS)
            val j2b = context.model.joins[target].orEmpty().map { join ->
                var enc = 0L
                for (d in join.dependencies)
                    enc = enc or (d2b[d] ?: 0L)
                return@map join to enc
            }
            return@computeIfAbsent d2b to j2b
        }

    override fun execute(join: Join?, split: Split?) {
        val removedDeps = executeJoin(join)
        val addedDeps = executeSplit(split)
        var newJoins = HashSet<Join?>()
        //val uniqueSet = uniqueSet().groupBy { it.target }
        //assert((join == null) == (joins == setOf(null)))
        if (join != null) {
            joins.filterTo(newJoins) { j -> j != null && removedDeps.all { d -> !j.contains(d) } }
            val (d2b, j2b) = retrieve(join.target)
            activeDepsEnc.compute(join.target) { _, v ->
                var removedEnc = 0L
                for (dep in removedDeps)
                    removedEnc = removedEnc or d2b[dep]!!
                return@compute (v ?: 0L) and (removedEnc.inv())
            }
            /*
            var v = activeDepsEnc[join.target]?:0
            for(dep in removedDeps)
                v = v and d2b[dep]!!.inv()
            activeDepsEnc[join.target] = v
             */
        } else {
            newJoins = HashSet(joins)
            if (null in joins)
                newJoins.remove(null)
        }
        /*
        for ((target, deps) in addedDeps.groupBy { it.target })
            for (join in model.joins[target].orEmpty()) {
                if (/*deps.any { it in join } &&*/ uniqueSet[target].orEmpty().containsAll(join.dependencies))
                    newJoins.add(join)
            }
         */
        for ((target, deps) in addedDeps.groupBy { it.target }) {
            val (d2b, j2b) = retrieve(target)
            val avail = activeDepsEnc.compute(target) { _, old ->
                var v = old ?: 0L
                for (dep in deps)
                    v = v or d2b[dep]!!
                return@compute v
            }!!
            /*
            var avail2 = 0L
            for (d in uniqueSet[target].orEmpty())
                avail2 = avail or d2b[d]!!
            assert(avail == avail2)
             */
            for (e in j2b) {
                if (avail and e.second == e.second) {
                    // assert(uniqueSet[target].orEmpty().containsAll(e.key.dependencies))
                    newJoins.add(e.first)
                }
            }
        }
        joins = newJoins
    }
}

class PerformanceAnalyzer(
    private val log: Log,
    private val model: CausalNet,
    private val distance: Distance = StandardDistance()
) {

    companion object {
        private val logger = logger()
    }

    private fun align(partialAlignment: Alignment, dec: DecoupledNodeExecution?, event: Event?): Alignment? {
        val newCost = partialAlignment.cost + distance(dec?.activity, event)
        if (newCost < distance.maxAcceptableDistance) {
            val nextState = CausalNetStateWithJoins(partialAlignment.state as CausalNetStateWithJoins)
            if (dec != null)
                nextState.execute(dec.join, dec.split)
            val step = AlignmentStep(event, dec?.activity, partialAlignment.state)
            val nextAlignment = HierarchicalIterable(partialAlignment.alignment, step)
            var nextPosition = partialAlignment.tracePosition
            if (event != null)
                nextPosition += 1
            val remainder = partialAlignment.context.events.subList(
                partialAlignment.tracePosition,
                partialAlignment.context.events.size
            )
            val minOccurrences = Counter<Node>()
            for (e in partialAlignment.state.entrySet())
                minOccurrences.compute(e.element.target) { _, v -> max(v ?: 0, e.count) }
            var heuristic = 0.0
            for ((n, minOcc) in minOccurrences.entries) {
                val skipDst = distance(n, null)
                val dsts = ArrayList<Double>()
                for (e in remainder) {
                    val d = distance(n, e)
                    if (d < skipDst)
                        dsts.add(d)
                }
                dsts.sort()
                for (i in 0 until minOcc)
                    heuristic += if (i < dsts.size) min(skipDst, dsts[i]) else skipDst
            }
            /*
            val heuristic = partialAlignment.state
                .mapToSet { it.target }
                .sumByDouble { n -> (remainder.map { e -> distance(n,e) } + listOf(distance(n, null))).min()?:0.0 }
             */
//            println("$heuristic ${partialAlignment.state} ${remainder.map {it.conceptName}}")
            return Alignment(newCost, heuristic, nextAlignment, nextState, nextPosition, partialAlignment.context)
        }
        return null
    }

    private class AlignmentComparator : Comparator<Alignment> {

        override fun compare(a: Alignment, b: Alignment): Int {
            for (i in a.features.indices) {
                val fa = a.features[i] //.value
                val fb = b.features[i] //.value
                val v = fa.compareTo(fb)
                if (v != 0)
                    return v
            }
            return 0
        }

    }

    private val stateContext = StateContext(model, HashMap())

    private fun initialAlignment(events: List<Event>): List<Alignment> {
        val context = AlignmentContext(model, events, distance)
        return listOf(
            Alignment(
                0.0,
                0.0,
                emptyList<AlignmentStep>(),
                CausalNetStateWithJoins(stateContext),
                0,
                context
            )
        )
    }

    internal fun allFreePartialAlignments(events: List<Activity>): Sequence<Alignment> = sequence {
        logger.trace { "prefix $events" }
        val queue = ArrayDeque<Alignment>()
        queue.addAll(initialAlignment(emptyList<Event>()))
        var skipCtr = 0
        while (queue.isNotEmpty()) {
            val partialAlignment = queue.poll()
            logger.trace { "queue size: ${queue.size} skip $skipCtr state ${partialAlignment.state} events: $events" }
            if (partialAlignment.tracePosition < events.size) {
                val event = events[partialAlignment.tracePosition]
                val decisions = model.available4(partialAlignment.state, event as Node)
                val remainder =
                    events.subList(partialAlignment.tracePosition + 1, events.size).groupingBy { it }.eachCount()
                for (dec in decisions) {
                    assert(dec.activity == event)
                    val nextState = CausalNetStateImpl(partialAlignment.state)
                    for (dep in dec.join?.dependencies.orEmpty())
                        nextState.remove(dep)
                    nextState.addAll(dec.split?.dependencies.orEmpty())
                    val ctr = Counter<Activity>()
                    for (e in nextState.entrySet())
                        ctr.compute(e.element.target) { _, v -> max(v ?: 0, e.count) }
                    val ignore = ctr.entries.any { e -> e.value > 1 + (remainder[e.key] ?: 0) }
                    if (ignore) {
//                        logger.trace{"Ignoring $nextState due to $remainder"}
                        skipCtr++
                        continue
                    }
//                    nextState.execute(dec.join, dec.split)
                    val step = AlignmentStep(null, dec.activity, partialAlignment.state)
                    val nextAlignment = HierarchicalIterable(partialAlignment.alignment, step)
                    val nextPosition = partialAlignment.tracePosition + 1
                    queue.add(Alignment(0.0, 0.0, nextAlignment, nextState, nextPosition, partialAlignment.context))
                }
            } else
                yield(partialAlignment)
        }
    }

    private fun modelReachedEnd(partialAlignment: Alignment): Boolean {
        return partialAlignment.state.isEmpty() && partialAlignment.alignment.any { it.activity != null }
    }

    private fun addMissingEnd(alignment: Alignment): Alignment {
        return alignment
    }

    private class CostCache : HashMap<AlignmentState, Double>() {
        override operator fun get(key: AlignmentState) = super.get(key) ?: Double.POSITIVE_INFINITY
    }

    private fun available(partialAlignment: Alignment): List<DecoupledNodeExecution> {
        val model = partialAlignment.context.model
        val result = ArrayList<DecoupledNodeExecution>()
        val joins = (partialAlignment.state as CausalNetStateWithJoins).activeJoins
        for (join in joins) {
            val node = join?.target ?: model.start
            val splits = if (node != model.end) model.splits[node].orEmpty() else setOf(null)
            for (split in splits)
                result.add(DecoupledNodeExecution(node, join, split))
        }
        return result
        /*
        with(partialAlignment) {
            return context.model.available(state)
        }
         */
    }

    /**
     * @return Alignment + number of visited states
     */
    internal fun computeOptimalAlignment(trace: Trace): Pair<Alignment, Int> {
        val bestCost = CostCache()
        val events = trace.events.toList()
        val queue = PriorityQueue(AlignmentComparator())
//        val queue = MinMaxPriorityQueue.orderedBy(AlignmentComparator()).maximumSize(100).create<Alignment>()
        queue.addAll(initialAlignment(events))
        var ctr = 0
        var skipCtr = 0
        while (queue.isNotEmpty()) {
            val partialAlignment = queue.poll()
            if (bestCost[partialAlignment.alignmentState] <= partialAlignment.cost) {
                skipCtr++
                continue
            }
            if (partialAlignment.state.isNotEmpty())
                bestCost[partialAlignment.alignmentState] = partialAlignment.cost
            ctr += 1
            val modelReachedEnd = modelReachedEnd(partialAlignment)
            val traceReachedEnd = partialAlignment.tracePosition >= events.size
            logger.trace { "Queue size: ${queue.size}, cost: ${partialAlignment.cost}+${partialAlignment.heuristic} pos: ${partialAlignment.tracePosition} model ended $modelReachedEnd trace ended $traceReachedEnd ${partialAlignment.state} | " + partialAlignment.alignment.joinToString { "${it.event?.conceptName ?: '⊥'} -> ${it.activity ?: '⊥'}" } }
            if (modelReachedEnd && traceReachedEnd) {
                logger.trace("Visited $ctr partial alignments, skipctr=$skipCtr")
                return addMissingEnd(partialAlignment) to ctr
            }
            // match is set to true if there is an alignment between possible decisions and current event
            // The idea is that one should not skip if it is possible to advance in log and model at the same time
            var match = false
            if (!modelReachedEnd) {
                val decisions = available(partialAlignment)
                if (!traceReachedEnd) {
                    val event = events[partialAlignment.tracePosition]
                    for (dec in decisions) {
                        val a = align(partialAlignment, dec, event)
                        if (a != null) {
                            match = true
                            if (bestCost[a.alignmentState] > a.cost)
                                queue.add(a)
                        }
                    }
                }
                if (!match)
                    for (dec in decisions) {
                        val a = align(partialAlignment, dec, null)
                        if (a != null && bestCost[a.alignmentState] > a.cost)
                            queue.add(a)
                    }
            }
            if (!match && !traceReachedEnd) {
                val a = align(partialAlignment, null, events[partialAlignment.tracePosition])
                if (a != null && bestCost[a.alignmentState] > a.cost)
                    queue.add(a)
            }
        }
        throw IllegalStateException("BUG: It was impossible to align the trace with the model.")
    }

    val optimalAlignment: List<Alignment> by lazy {
        val n = if (logger.isDebugEnabled) log.traces.count() else 0
        log.traces.mapIndexed { idx, trace ->
//            logger.debug ( "Optimal alignment progress: ${idx + 1}/$n")
            computeOptimalAlignment(trace).first
        }.toList()
    }

    /**
     * Cost of the shortest valid sequence in the model
     */
    internal val movem: Double by lazy {
        computeOptimalAlignment(Trace(emptySequence())).first.cost
    }

    internal val movel: Double by lazy {
        log.traces.sumByDouble { trace -> trace.events.sumByDouble { distance(null, it) } }
    }

    val fcost: Double by lazy {
        optimalAlignment.sumByDouble { it.cost }
    }

    val fitness: Double by lazy {
        1.0 - fcost / (movel + optimalAlignment.size * movem)
    }

    private val prefix2Step: Map<List<Activity>, List<AlignmentStep>> by lazy {
        val result = HashMap<List<Activity>, ArrayList<AlignmentStep>>()
        for (alignment in optimalAlignment) {
            val prefix = ArrayList<Activity>()
            for (step in alignment.alignment) {
                result.getOrPut(ArrayList(prefix)) { ArrayList() }.add(step)
                if (step.activity != null)
                    prefix.add(step.activity)
            }
        }
        return@lazy result
    }

    private val prefix2Possible: Map<List<Activity>, Set<Activity>> by lazy {
        possibleNext(prefix2Step.keys)
    }


    internal fun possibleNext(prefixes: Collection<List<Activity>>): Map<List<Activity>, Set<Activity>> =
        possibleNextOnPartialAlignments(prefixes)
    //possibleNextUsingGurobiBySearch(prefixes.toList())
//        prefixes.associateWith { prefix -> possibleNextUsingGurobi(prefix) }

    internal fun distanceOnJoins(to: Activity): Map<Activity, Int> {
        val queue = ArrayDeque<Activity>()
        val result = HashMap<Activity, Int>()
        queue.add(to)
        result[to] = 0
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            val base = result[current]!!
            val minJoin = HashMap<Node, Int>()
            for (join in model.joins[current].orEmpty())
                for (s in join.sources)
                    minJoin.compute(s) { _, v ->
                        if (v == null)
                            join.size
                        else
                            min(v, join.size)
                    }
            for ((n, ctr) in minJoin) {
                result.compute(n) { _, v ->
                    if (v == null || v > base + ctr) {
                        queue.add(n)
                        base + ctr
                    } else if (n == to && v == 0) {
                        base + ctr
                    } else
                        v
                }
            }
        }
        logger.trace { "distances@joins($to)=$result" }
        return result
    }

    internal fun distanceOnDependencies(to: Activity): Map<Activity, Int> {
        val queue = ArrayDeque<Activity>()
        val result = HashMap<Activity, Int>()
        queue.add(to)
        result[to] = 0
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            val base = result[current]!!
            for (dep in model.incoming[current].orEmpty()) {
                val n = dep.source
                result.compute(n) { _, v ->
                    if (v == null || v > base + 1) {
                        queue.add(n)
                        base + 1
                    } else if (n == to && v == 0) {
                        base + 1
                    } else
                        v
                }
            }
        }
        logger.trace { "distances($to)=$result" }
        return result
    }

    private val distanceToEnd by lazy {
        distanceOnDependencies(model.end)
    }

    private val distanceToSelf by lazy {
        model.activities.associateWith { n ->
            distanceOnDependencies(n)[n] ?: Int.MAX_VALUE
        }
    }

    internal fun replayWithSearch(prefix: List<Node>): Boolean {
        val minSolutionLength = prefix.indices.map { i -> 1 + i + distanceToEnd[prefix[i]]!! }.max()!!

        data class SearchState(
            val position: Int,
            val ctr: Int,
            val node: Activity?,
            val state: CausalNetState,
            val trace: Iterable<Node>
        ) : HasFeatures() {
            val length: Int
                get() = position + (if (node != null) 1 else 0)
            override val features: List<Fraction>
                get() = listOf(
                    Fraction.getFraction(cost, 1),
                    //Fraction.getFraction(ctr, 1),
                    // if(length<prefix.size) Fraction.ZERO else Fraction.getFraction(-length, 1),
                    //if(length<prefix.size) Fraction.ZERO else Fraction.getFraction(state.size, 1)
                    Fraction.getFraction(stateSize, 1)
                )
            val cost: Int
            val stateSize: Int

            init {
                val targets = state
                    .entrySet()
                    .groupBy { it.element.target }
                    .mapValues { it.value.map { e -> e.count }.max()!! }
                val m = minDistanceToEnd(targets)
                var ctr = 0
                if (length < prefix.size) {
                    val remainder = prefix.subList(length, prefix.size).groupingBy { it }.eachCount()
                    for (e in targets)
                        ctr += max(e.value - (remainder[e.key] ?: 0), 0)
                } else
                    ctr = targets.values.sum()
                cost = length + m
                stateSize = ctr

            }

            var debug: List<Int>? = null

            private fun minDistanceToEnd(_targets: Map<Node, Int>): Int {
                val targets = _targets.toMutableMap()
                if (position < prefix.size) {
                    val tmp = prefix.subList(position + 1, prefix.size).groupingBy { it }.eachCount()
                    for (e in tmp)
                        targets.compute(e.key) { _, v ->
                            max(e.value, v ?: 0)
                        }
                }
                //logger.trace{"targets=$targets"}
                val a = targets.entries.map { (n, count) ->
                    distanceToEnd[n]!! + (count - 1) * (distanceToSelf[n] ?: Int.MAX_VALUE)
                }.min() ?: 0
                val b = targets.size
                val c = prefix.size - length
                val d = minSolutionLength - length
                val tmp = listOf(a, b, c, d)
                this.debug = tmp
                //logger.trace{"possible distances $tmp"}
                return tmp.max()!!
            }
        }

        logger.trace { "toEnd=$distanceToEnd" }
        logger.trace { "toSelf=$distanceToSelf" }
        val queue = PriorityQueue<SearchState>()
        queue.add(SearchState(0, 0, model.start, CausalNetStateImpl(), listOf(model.start)))
        var visitedCtr = 0
        var lastCost = 0
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            if (lastCost != current.cost) {
                logger.debug { "Current cost: ${current.cost}" }
                lastCost = current.cost
            }
            visitedCtr++
            if (visitedCtr % 1000 == 0)
                logger.debug { "visited $visitedCtr queue size ${queue.size} cost=$lastCost" }
            logger.trace {
                "$prefix ${current.features} ${current.debug} pos=${current.position} me=$visitedCtr parent=${current.ctr} ${current.node} ${current.trace.toList()} ${
                    CausalNetStateImpl(
                        current.state
                    )
                }"
            }
            if (current.node != null) {
                assert(current.position >= prefix.size || current.node == prefix[current.position])
                if (current.node == model.end) {
                    if (current.state.isEmpty()) {
                        logger.trace { "$prefix SUCCESS: visited $visitedCtr ${current.trace.toList()}" }
                        return true
                    }
                } else {
                    var nextNode = if (current.position + 1 < prefix.size) prefix[current.position + 1] else null
                    if (nextNode != null) {
                        if (model.joins[nextNode].orEmpty()
                                .any { join -> current.state.containsAll(join.dependencies) }
                        )
                            nextNode = null //next node is already active
                        else if (Dependency(current.node as Node, nextNode) !in model.incoming[nextNode].orEmpty()) {
                            logger.trace { "Invalid state" }
                            continue    //next node is not active and cannot be activate due to missing dependency
                        }
                    }
                    for (split in model.splits[current.node].orEmpty()) {
                        val newState = LazyCausalNetState(current.state, emptyList(), split.dependencies)
                        // ignore states with multiple tokens on a single dependency
                        if (newState.entrySet().any { it.count >= 2 })
                            continue
                        if (nextNode == null || model.joins[nextNode].orEmpty()
                                .any { join -> newState.containsAll(join.dependencies) }
                        ) {
                            val s = SearchState(current.position + 1, visitedCtr, null, newState, current.trace)
                            logger.trace { "$split OK ${s.features}" }
                            queue.add(s)
                        } else
                            logger.trace { "$split FAIL" }
                    }
                }
            } else {
                val deps = current.state.uniqueSet().groupBy { it.target }
                val candidateNodes = if (current.position < prefix.size)
                    setOf(prefix[current.position])
                else
                    deps.keys
                for (n in candidateNodes) {
                    val sources = deps[n]?.mapToSet { it.source } ?: emptySet()
                    val newTrace = HierarchicalIterable(current.trace, n)
                    for (join in model.joins[n].orEmpty()) {
                        if (sources.containsAll(join.sources)) {
                            val newState = LazyCausalNetState(current.state, join.dependencies, emptyList())
                            queue.add(SearchState(current.position, visitedCtr, n, newState, newTrace))
                        }
                    }
                }
            }
        }
        logger.trace { "$prefix FAILED visited $visitedCtr" }
        return false
    }

    val gurobiModel: GurobiModel by lazy {
        val maxLen = 2 * (log.traces.map { it.events.count() }.max() ?: 50)   //as good a default as any?
        //TODO max tokens on dependency could also be derived from log? like max of number of repetitions of any activity?
        GurobiModel(model, maxLen, 1)
    }


    internal fun possibleNextUsingGurobiBySearch(prefixes: List<List<Activity>>): Map<List<Activity>, Set<Activity>> {
        require(prefixes.all { it.size < gurobiModel.maxLen })
        val result = HashMap<List<Activity>, Set<Activity>>()
        val todo = HashMap<List<Activity>, Set<Activity>>()
        println("PREFIXES")
        for (p in prefixes) {
            val u = possibleNextUpperApproximation(p)
            if (u.size == 1)
                result[p] = u
            else
                todo[p] = u
            println("$u $p")
        }
        println()
        println()
        //TODO batching prefixes?
        if (todo.isNotEmpty())
            result.putAll(gurobiModel.solve2(todo))
        return result
    }

    internal fun possibleNextUsingGurobiByQuery(prefixes: Collection<List<Activity>>): Map<List<Activity>, Set<Activity>> {
        val extendedPrefixes = prefixes
            .flatMap { prefix -> possibleNextUpperApproximation(prefix).map { prefix + listOf(it) } }
        val batchSize = 1000
        val solutions = ArrayList<List<ActivityBinding>?>()
        for (start in extendedPrefixes.indices step batchSize) {
            val end = min(start + batchSize, extendedPrefixes.size)
            val inp = extendedPrefixes.subList(start, end)
            solutions.addAll(gurobiModel.solve(inp))
        }
        val result = HashMapWithDefault<List<Activity>, HashSet<Activity>>() { HashSet() }
        for ((extendedPrefix, solution) in extendedPrefixes zip solutions) {
            if (solution != null) {
                val prefix = extendedPrefix.subList(0, extendedPrefix.size - 1)
                result[prefix].add(extendedPrefix.last())
            }
        }
        return result
    }

    internal fun possibleNextUsingReplayAndSearch(prefix: List<Activity>): Set<Activity> {
        val upper = possibleNextUpperApproximation(prefix)
        val result = upper.filterTo(HashSet()) { a ->
            val extendedPrefix = (prefix + listOf(a)) as List<Node>
            return@filterTo replayWithSearch(extendedPrefix)
        }
        logger.trace { "$prefix upper=$upper result=$result" }
        return result
    }

    internal fun possibleNextUsingQuerying(prefix: List<Activity>): Set<Activity> {
        val hell = CausalNetVerifierImpl(model)
        val upper = possibleNextUpperApproximation(prefix)
        val result = upper.filterTo(HashSet()) { a ->
            val extendedPrefix = (prefix + listOf(a)) as List<Node>
            hell.anyValidSequence(extendedPrefix) != null
        }
        logger.trace { "$prefix upper=$upper result=$result" }
        return result
    }

    internal fun possibleNextUsingValidSequences(prefix: List<Activity>): Set<Activity> {
        logger.debug { "possibleNext($prefix)" }
        val result = HashSet<Activity>()
        val hell = CausalNetVerifierImpl(model)
        // valid sequences runs BFS and there is only a finite number of possible successors, so I think this terminates
        val seqs = hell.computeSetOfValidSequences(false) { seq ->
            val activities = seq.map { it.a }
            if (activities.size <= prefix.size)
                return@computeSetOfValidSequences activities == prefix.subList(0, activities.size)
            return@computeSetOfValidSequences activities[prefix.size] !in result
        }
        for (seq in seqs) {
            logger.debug("Hello, nurse! ${seq.size} ${prefix.size}")
            if (seq.size > prefix.size) {
                val activities = seq.map { it.a }
                logger.trace { "$activities" }
                assert(activities.subList(0, prefix.size) == prefix)
                result.add(activities[prefix.size])
            }
        }
        return result
    }

    internal fun possibleNextUpperApproximation(prefix: List<Activity>): Set<Activity> {
        if (prefix.isEmpty())
            return setOf(model.start)
        val ctr = Counter<Activity>()
        prefix.flatMap { model.outgoing[it]?.map { dep -> dep.target }.orEmpty() }.groupingBy { it }.eachCountTo(ctr)
        for (n in prefix.subList(1, prefix.size)) {
            ctr.compute(n) { _, v ->
                v!! - 1
            }
        }
        logger.trace { "$prefix -> $ctr" }
        return ctr.entries.filter { it.value >= 1 }.mapToSet { it.key }
    }

    /**
     * This can return too many activities in some cases. A notable example is:
    causalnet {
    start splits a
    a splits a+b or c
    b splits b or end
    c splits b
    start or a join a
    c or a+b join b
    a joins c
    b joins end
    }

    For prefix 'start a a c b' this returns 'b end*', but the model enforces that 'b' is executed exactly the
    same number of times as 'a', so the only possible activity is, in fact, 'a'
     */
    internal fun possibleNextOnPartialAlignments(prefix: List<Activity>): Set<Activity> {
        val maxPartialAlignments = 1
        if (prefix.isEmpty())
            return setOf(model.start)
        val result = HashSet<Activity>()
        for (alignment in allFreePartialAlignments(prefix).take(maxPartialAlignments)) {
            val state = alignment.state.uniqueSet().groupBy { it.target }
            for ((node, deps) in state.entries) {
                val srcs = deps.mapToSet { it.source }
                if (model.joins[node].orEmpty().any { join -> srcs.containsAll(join.sources) }) {
                    logger.trace { "node=$node joins=${model.joins[node]} sources=$srcs" }
                    result.add(node)
                }
            }
        }
        return result
    }

    class PrefixTrie(
        val activity: Activity?,
        val label: List<Activity>,
        val children: Map<Activity?, PrefixTrie>
    ) {

        fun flatten(): Sequence<List<Activity>> = sequence {
            if (activity == null)
                yield(label)
            else
                for (child in children.values)
                    yieldAll(child.flatten())
        }

        /*
        fun asList(): List<Activity> {
            var n = this
            val result = ArrayList<Activity>()
            while (n.parent != null) {
                if (n.activity != null)
                    result.add(n.activity!!)
                n = n.parent!!
            }
            result.add(n.activity!!)
            return result.reversed()
        }
         */

        fun dump(indent: String = "") {
            println("$indent$activity")
            for (child in children.values)
                child.dump("$indent  ")
        }

        companion object {
            fun build(prefixes: Collection<List<Activity>>): PrefixTrie {
                val tries = build(emptyList(), prefixes, 0)
                return tries.values.single()
            }

            private fun build(
                label: List<Activity>,
                prefixes: Collection<List<Activity>>,
                pos: Int
            ): Map<Activity?, PrefixTrie> =
                prefixes
                    .groupBy { if (pos < it.size) it[pos] else null }
                    .mapValues { (activity, relevantPrefixes) ->
                        if (activity != null) {
                            val newLabel = label + listOf(activity)
                            PrefixTrie(activity, newLabel, build(newLabel, relevantPrefixes, pos + 1))
                        } else
                            PrefixTrie(activity, label, emptyMap())
                    }
        }
    }

    internal fun possibleNextOnPartialAlignments(prefixes: Collection<List<Activity>>): Map<List<Activity>, Set<Activity>> {
        data class SearchState(
            val position: Int,
            val produce: Boolean,
            val relevantPrefixes: PrefixTrie,
            val state: CausalNetStateWithJoins
        )

        val maxPartialAlignments = 1
        val result = HashMap<List<Activity>, HashSet<Activity>>()
        result[emptyList<Activity>()] = hashSetOf<Activity>(model.start)
        for (prefix in prefixes) {
            if (prefix.isNotEmpty() && prefix.last() == model.end)
                result[prefix] = hashSetOf<Activity>(model.end)   //FIXME huh wtf?
        }
        // number of visited states, nasa, first 30 traces
        // b.pos-a.pos b.pr-a.pr b.state-a.state  -> 1021735
        // a.pos-b.pos b.pr-a.pr b.state-a.state  -> OOM, bo kolejka rosnie
        // b.pos-a.pos b.pr-a.pr b.rel-a.rel      -> 1567146 i rosnie
        // b.pos-a.pos b.pr-a.pr a.rel-b.rel      -> 6921708 i rosnie
        // b.pos-a.pos b.pr-a.pr a.state-b.state  -> 6428902 i rosnie
        // b.state-a.state                        ->  541520
        // b.state-a.state b.pos-a.pos            ->  541789
        // b.active-a.active                      -> 3039034 i rosnie
        val queue = PriorityQueue<SearchState>(kotlin.Comparator { a, b ->
            return@Comparator b.state.size - a.state.size
            //if (a.position != b.position)
            //    return@Comparator b.position - a.position
            //if (a.produce != b.produce)
            //    return@Comparator (if (b.produce) 1 else 0) - (if (a.produce) 1 else 0)
            //return@Comparator b.state.size - a.state.size
//            if(b.state.size != a.state.size)
//                return@Comparator b.state.size - a.state.size
//            return@Comparator b.position - a.position
            //return@Comparator a.relevantPrefixes.size - b.relevantPrefixes.size
        })
        var ctr = 0
        //val queue = ArrayDeque<SearchState>()
        val nonEmptyPrefixes = prefixes - result.keys

        val missing = nonEmptyPrefixes.toMutableSet()
        if (nonEmptyPrefixes.isNotEmpty()) {
            queue.add(
                SearchState(
                    0,
                    true,
                    PrefixTrie.build(nonEmptyPrefixes),
                    CausalNetStateWithJoins(StateContext(model, HashMap()))
                )
            )
            while (missing.isNotEmpty() && queue.size > 0) {
                val current = queue.poll()
                if (current.relevantPrefixes.flatten().all { it !in missing })
                    continue
                /*
                assert(current.relevantPrefixes.isNotEmpty())
                if (current.relevantPrefixes.all { it !in missing })
                    continue
                 */
                ctr++
                //println("#missing ${missing.size}/${nonEmptyPrefixes.size} #queue ${queue.size} $current ${current.state.activeJoins}")
                if (current.produce) {
                    /*
                    val currentNode = current.relevantPrefixes[0][current.position]
                    val byNextNode =
                        current.relevantPrefixes.groupBy { if (current.position + 1 < it.size) it[current.position + 1] else null }
                     */
                    val currentNode = current.relevantPrefixes.activity
                    check(currentNode != null)
                    for (split in model.splits[currentNode].orEmpty()) {
                        //val nextState = LazyCausalNetState(current.state, emptyList(), split.dependencies)
                        val nextState = CausalNetStateWithJoins(current.state)
                        nextState.execute(null, split)
                        val keys = hashSetOf<Activity?>(null)
                        nextState.activeJoins.filterNotNull().mapTo(keys) { it.target }
                        val relevantChildren = current.relevantPrefixes.children.filterKeys { it in keys }
                        val relevant = PrefixTrie(current.relevantPrefixes.activity, current.relevantPrefixes.label, relevantChildren)
                        /*
                        val relevant = ArrayList<List<Activity>>()
                        relevant.addAll(byNextNode[null] ?: emptyList())
                        for (target in nextState.activeJoins.filterNotNull().mapToSet { it.target })
                            relevant.addAll(byNextNode[target] ?: emptyList())
                        if (relevant.isNotEmpty())
                        //if(nextState.activeJoins.filterNotNull().mapToSet { it.target }.any { it in possibleNext })
                            queue.add(SearchState(current.position + 1, false, relevant.filterNotNull(), nextState))
                         */
                        queue.add(SearchState(current.position + 1, false, relevant, nextState))
                    }
                    /*
                    for (split in model.splits[currentNode].orEmpty()) {
                        //val nextState = LazyCausalNetState(current.state, emptyList(), split.dependencies)
                        val nextState = CausalNetStateWithJoins(current.state)
                        nextState.execute(null, split)
                        //if(nextState.activeJoins.filterNotNull().mapToSet { it.target }.any { it in possibleNext })
                        queue.add(SearchState(current.position + 1, false, current.relevantPrefixes, nextState))
                    }
                     */
                } else {
                    for ((candidate, activeJoins) in current.state.activeJoins.filterNotNull().groupBy { it.target }) {
                        //val prefixSoFar = current.relevantPrefixes[0].subList(0, current.position)
                        val prefixSoFar = current.relevantPrefixes.asList()
                        result.computeIfAbsent(prefixSoFar) { HashSet() }.add(candidate)
                        if (missing.remove(prefixSoFar))
                            println("#missing ${missing.size}/${nonEmptyPrefixes.size} #solved prefix ${prefixSoFar.size} #queue ${queue.size} #ctr $ctr")
                        //val relevant =
                        //    current.relevantPrefixes.filter { current.position < it.size && it[current.position] == candidate && it in missing }
                        val relevant = current.relevantPrefixes.children[candidate]
                        if (/*relevant.isNotEmpty()*/ relevant != null) {
                            val smallestJoinSize = activeJoins.map { it.size }.min()!!
                            val smallestJoins = activeJoins.filter { it.size == smallestJoinSize }
                            for (join in smallestJoins) {
                                //val nextState = LazyCausalNetState(current.state, join.dependencies, emptyList())
                                val nextState = CausalNetStateWithJoins(current.state)
                                nextState.execute(join, null)
                                queue.add(SearchState(current.position, true, relevant, nextState))
                            }
                        }
                    }
                }
            }
        }
        assert(missing.size == 0)
        println("#ctr $ctr")
        println(result)
        return result.filterKeys { it in prefixes }
    }


    private fun pnew(w: Int, n: Int): Double =
        if (n >= w + 2) (w * (w + 1)).toDouble() / (n * (n - 1)).toDouble() else 1.0

    private val generalization: Pair<Double, Double> by lazy {
        val state2Event = Counter<CausalNetState>()
        val state2Activity = HashMap<CausalNetState, HashSet<Activity>>()
        for (alignment in optimalAlignment) {
            val steps = alignment.alignment.toList()
            for ((sidx, step) in alignment.alignment.withIndex()) {
                if (sidx > 0 && step.activity != null && step.event != null) {
                    var lastPossiblyFreeState: CausalNetState? = null
                    for (bidx in sidx - 1 downTo 0) {
                        val before = steps[bidx]
                        val ready = model.available(before.stateBefore).mapToSet { it.activity }
                        if (step.activity !in ready) {
                            lastPossiblyFreeState = before.stateBefore
                            break
                        }
                    }
                    check(lastPossiblyFreeState != null) { "No free state for ${step.activity}. This is outright impossible, there should always be a free state right before start." }
                    state2Event.inc(lastPossiblyFreeState)
                    state2Activity.getOrPut(lastPossiblyFreeState) { HashSet() }.add(step.activity)
                }
            }
        }
        var eventLevel = 0.0
        var stateLevel = 0.0
        var nEvents = 0
        val nStates = state2Activity.keys.size
        for ((s, diff) in state2Activity) {
            val w = diff.size
            val n = state2Event[s]
            val p = pnew(w, n)
            eventLevel += n * p
            stateLevel += p
            nEvents += n
        }
        return@lazy (1.0 - eventLevel / nEvents) to (1.0 - stateLevel / nStates)
    }

    val eventLevelGeneralization: Double
        get() = generalization.first

    val stateLevelGeneralization: Double
        get() = generalization.second

    internal fun precisionHelper(prefix2Possible: Map<List<Activity>, Set<Activity>>): Double {
        var result = 0.0
        val prefix2Next = prefix2Step.mapValues { it.value.mapToSet { it.activity }.filterNotNull() }
//      check(prefix2Possible[emptyList()].isNullOrEmpty())
        var ctr = 0
        for (alignment in optimalAlignment) {
            val prefix = ArrayList<Activity>()
            for (step in alignment.alignment) {
                if (step.activity != null) {
                    val observed = prefix2Next.getValue(prefix)
                    val possible = if (prefix.isNotEmpty()) prefix2Possible.getValue(prefix) else setOf(model.start)
                    logger().trace { "observed=$observed possible=$possible" }
                    check(possible.containsAll(observed)) { "prefix=$prefix observed=$observed possible=$possible" }
                    result += observed.size.toDouble() / possible.size.toDouble()
                    ctr += 1
                    prefix.add(step.activity)
                }
            }
        }
        return result / ctr
    }

    val precision: Double by lazy {
        precisionHelper(prefix2Possible)
    }

    override fun toString(): String =
        "fitness=$fitness precision=$precision eGeneralization=$eventLevelGeneralization sGeneralization=$stateLevelGeneralization"
}

