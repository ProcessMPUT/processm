package processm.experimental.performance

import org.apache.commons.collections4.multiset.HashMultiSet
import org.apache.commons.math3.fraction.BigFraction
import processm.core.log.Event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.*
import processm.core.models.commons.Activity
import processm.core.verifiers.causalnet.CausalNetVerifierImpl
import processm.helpers.*
import processm.logging.debug
import processm.logging.logger
import processm.logging.trace
import processm.miners.causalnet.onlineminer.HasFeatures
import processm.miners.causalnet.onlineminer.LazyCausalNetState
import java.util.*
import kotlin.math.max
import kotlin.math.min

class PerformanceAnalyzer(
    private val log: Log,
    private val model: CausalNet,
    private val distance: Distance = StandardDistance()
) {
    companion object {
        private val logger = logger()
    }

    private val encoder = object : HashMap<Node, UInt128>() {
        var ctr = UInt128(1UL)
        override operator fun get(key: processm.core.models.causalnet.Node) = computeIfAbsent(key) {
            val old = ctr.copy()
            ctr.shl(1)
            check(!ctr.isZero()) { "There are too many nodes in the model" }
            return@computeIfAbsent old
        }

        operator fun get(nodes: Iterable<processm.core.models.causalnet.Node>): UInt128 {
            val r = UInt128()
            for (n in nodes)
                r.or(this[n])
            return r
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private val sourcesCache = object : HashMap<Node, List<UInt128>>() {
        override operator fun get(key: processm.core.models.causalnet.Node) = computeIfAbsent(key) {
            return@computeIfAbsent model.joins[key]?.map { encoder[it.sources.toSet()] }.orEmpty()
        }
    }

    //This is a relaxed version of the problem assuming that there's always exactly one split consisting of all the outgoing dependencies and that joins don't consume tokens, only require their presence
    private val reachableCache = object : HashMap<Set<Dependency>, Set<Node>>() {
        override operator fun get(key: Set<Dependency>): Set<processm.core.models.causalnet.Node> =
            computeIfAbsent(key) {
                logger.trace { "key=$key" }
                var freshReachable = HashSet<processm.core.models.causalnet.Node>()
                for (n in key.mapToSet { it.target })
                    if (model.joins[n].orEmpty().any { key.containsAll(it.dependencies) })
                        freshReachable.add(n)
                val aux = encoder[key.mapToSet { it.source }]
                /*
                var freshReachable = HashSet(key)
                for(n in key)
                    for(dep in model.outgoing[n].orEmpty())
                        freshReachable.add(dep.target)
    */
                val reachable = HashSet(freshReachable)
                var reachableEnc = encoder[reachable]
                while (true) {
                    val newReachable = HashSet<processm.core.models.causalnet.Node>()
                    var newReachableEnc = UInt128()
                    for (node in freshReachable) {
                        for (dep in model.outgoing[node].orEmpty()) {
                            val t = encoder[dep.target]
                            val tmp = reachableEnc.copy()
                            tmp.or(newReachableEnc)
                            tmp.and(t)
                            if (!tmp.isZero())
                                continue
                            /* I think one cannot perform filering on outgoing dependences.
                            If we managed to reach this node, any split can be executed here (from a local point of view),
                            so all successor nodes are reachable.
                            Can be seen in `BPIC15_2f - h1 admissibility`

                            On the other hand, one could and should at least filter out nodes that are NOT reachable because
                            they depend on a node that is not reachable anymore.

                            I am utterly confused.
                            */
                            val joins = sourcesCache[dep.target]
                            val reachableEncOrAux = reachableEnc.copy()
                            reachableEncOrAux.or(aux)
                            if (joins.any { j ->
                                    val tmp = j.copy()
                                    tmp.and(reachableEncOrAux)
                                    return@any tmp == j
                                }) {
                                //if (true) {
                                newReachable.add(dep.target)
                                reachable.add(dep.target)
                                newReachableEnc.or(t)
                            }
                        }
                    }
                    if (newReachableEnc.isZero())
                        break
                    freshReachable = newReachable
                    reachableEnc.or(newReachableEnc)
                    /*
                                    freshReachable = freshReachable
                                        .flatMapTo(HashSet()) { node ->
                                            model
                                                .outgoing[node]
                                                ?.mapToSet { dep -> dep.target }
                                                ?.filter {
                                                    reachableEnc and encoder[it] == UInt128.ZERO && sourcesCache[it].any { j-> j and reachableEnc == j }
                                                }.orEmpty()
                                        }
                                    if(freshReachable.isEmpty())
                                        break
                                    reachable.addAll(freshReachable)
                                    reachableEnc = reachableEnc or encoder[freshReachable]
                    */
                    //assert(encoder[reachable] == reachableEnc)
                }
                return@computeIfAbsent reachable
            }

    }

    private fun computeMinOccurrences3(state: CausalNetStateWithJoins): Map<Node, Int> {
        val result = HashMap<Node, Int>()
        for ((target, relevantEntries) in state.entrySet().groupBy({ it.element.target }, { it.copy() })) {
            val relevant = HashMultiSet<Node>()
            for (e in relevantEntries)
                relevant.add(e.element.source, e.count.toInt())
            val joins = model.joins[target]?.map { it.sources }.orEmpty()
            var ctr = 0
            while (relevant.isNotEmpty()) {
                val best = joins.maxByOrNull { join -> join.count { n -> n in relevant } }!!
                ctr++
                var last = false
                while (!last) {
                    last = false
                    for (node in best)
                        last = last or (relevant.remove(node, 1) == 1)
                }
            }
            result[target] = ctr
        }
        return result
    }

    private fun computeMinOccurrences(state: CausalNetStateWithJoins): Counter<Node> {
        val minOccurrences = Counter<Node>()
        for (e in state.entrySet())
            minOccurrences.compute(e.element.target) { _, v -> max(v ?: 0, e.count.toInt()) }
        return minOccurrences
    }

    private val largestJoins = object : HashMap<Node, List<Join>>() {
        override fun get(key: processm.core.models.causalnet.Node): List<Join> = computeIfAbsent(key) {
            val allJoins = model.joins[key] ?: return@computeIfAbsent emptyList()
            return@computeIfAbsent allJoins.filter { join ->
                allJoins.all { other ->
                    join === other || !other.dependencies.containsAll(
                        join.dependencies
                    )
                }
            }
        }
    }


    private fun computeMinOccurrences2(state: CausalNetStateWithJoins): Map<Node, Int> {
        val depCounter = state.entrySet().associate { it.element to it.count }
        val byTarget = state.entrySet().groupBy({ it.element.target }, { it.copy() })
        val minOccurrences = HashMap<Node, Int>()
        for ((target, deps) in byTarget) {
            val relevantStateSubset = deps.mapToSet { it.element }
            /*val relevantJoins = largestJoins[target]
                .mapNotNullTo(HashSet()) { /*val tmp = it.dependencies.intersect(relevantStateSubset)
                    if(tmp.isNotEmpty())
                        tmp.mapToSet { it.source }
                    else
                        null*/
                    if(it.dependencies.any { dep -> dep in relevantStateSubset })
                        it.sources
                    else
                        null
                }
            val maximalJoins = relevantJoins.filter {join ->
                relevantJoins.all { other -> other === join || !other.containsAll(join) }
            }
            val families = separateDisjointFamilies(maximalJoins)
            if(maximalJoins.size>1)
                println("F=$families")
            minOccurrences[target] = families.keys.sumBy { it.maxOfOrNull { src -> depCounter[Dependency(src, target)]?:0 }?:0 }
                */
            val universe = relevantStateSubset.mapToSet { it.source }
            val joins = model.joins[target]?.map { it.sources }.orEmpty()
            val covering = notReallySetCovering(universe, joins.map { it.toSet() }).map { joins[it] }
            assert(covering.size <= universe.size) { "Covering $covering is larger than its universe $universe" }
//            if(covering.size>1)
//                println("covering=$covering for $universe at $target with $joins")
//            val missing = covering.flatten()-relevantStateSubset.map { it.source }
////            if(missing.isNotEmpty())
//            println("missing=$missing")
            minOccurrences[target] = covering.size
        }
        return minOccurrences
    }

    /**
     * Estimate cost for any event that is yet to be aligned - it either can be aligned with some (possibly) reachable activity or must be skipped
     *
     * This works correctly as long as there are no skips in the trace. Otherwise, this may overestimate, because
     */
    private fun minCostOfEventAlignments(
        remainder: List<Event>,
        cheap: List<List<Pair<Node, Double>>>,
        nextState: CausalNetStateWithJoins,
        partialAlignment: Alignment
    ): Double {
        val reachable = when {
            nextState.isNotEmpty() -> reachableCache[nextState.uniqueSet().toSet()]
            //nextPosition == 0 -> model.instances
            //else -> emptySet()
            partialAlignment.alignment.all { it.activity == null } -> model.instances
            else -> emptySet()
        }
        if (reachableCache.size >= 10000)
            reachableCache.clear()

        var h1 = 0.0
        for ((i, e) in remainder.withIndex()) {
            var m = distance(null, e)
            if (m > 0.0) {
                for ((n, c) in cheap[i])
                    if ((c < m) && (n in reachable)) {
                        m = c
                        if (m == 0.0)
                            break
                    }
                h1 += m
            }
        }
        return h1
    }

    private fun doSomeMagic(nextState: CausalNetStateWithJoins): MutableMap<Node, Int> {
        val joinsThatMustBeUsed = HashMap<Join, Int>()
        for (e in nextState.entrySet()) {
            val join = model.joins[e.element.target]?.singleOrNull { e.element in it }
            //joins of size 1 are not interesting here - but must filter it afterwards, because it still must be the only possible join
            if (join != null && join.size >= 2)
                joinsThatMustBeUsed.compute(join) { _, v ->
                    if (v != null) max(
                        e.count.toInt(),
                        v
                    ) else e.count.toInt()
                }
        }
        val requiredTokens = HashMap<Dependency, Int>()
        for ((join, ctr) in joinsThatMustBeUsed)
            for (dep in join.dependencies)
                requiredTokens.compute(dep) { _, v -> if (v != null) ctr + v else ctr }
        for (e in nextState.entrySet())
            requiredTokens.compute(e.element) { _, v -> if (v != null && v > e.count) v - e.count else null }
        val minOccurrences = computeMinOccurrences(nextState)
        for ((dep, ctr) in requiredTokens) {
            //            minOccurrences.compute(dep.source) { s, v -> if (v != null) max(ctr, v) else ctr }
            //            minOccurrences.compute(dep.source) { s, v -> if (v == null || v < ctr) {println("Hit: $s $v -> $ctr"); ctr} else v }
            minOccurrences.compute(dep.source) { _, v -> if (v == null || v < ctr) ctr else v }
        }
        return minOccurrences
    }

    private val intersectionOfTargets = HashMapWithDefault<Node, Set<Node>> { key ->
        var intersection: Set<Node>? = null
        for (split in model.splits[key].orEmpty()) {
            intersection = intersection?.intersect(split.targets.toSet()) ?: split.targets.toSet()
            if (intersection.isEmpty())
                break
        }
        intersection.orEmpty()
    }

    /*
     * Minimalna liczba wystapien wierzcholka to maksymalna liczba tokenow, ktore musza sie pojawic na dowolnej krawedzi.
     * To jest poprawne, ale niestabilne
     */
    private fun pushForward(minOccurrences: MutableMap<Node, Int>): MutableMap<Node, Int> {
        var fresh: Collection<Pair<Node, Int>> = minOccurrences.entries.map { it.key to it.value }
        while (fresh.isNotEmpty()) {
            val new = HashMap<Node, Int>()
            for ((src, ctr) in fresh) {
                for (t in intersectionOfTargets[src])
                    new.compute(t) { _, v -> if (v == null || v < ctr) ctr else v }
            }
            val newFresh = ArrayList<Pair<Node, Int>>()
            for ((t, ctr) in new)
                minOccurrences.compute(t) { t, v ->
                    if (v == null || ctr > v) {
                        newFresh.add(t to ctr)
                        return@compute ctr
                    } else
                        return@compute v
                }
            fresh = newFresh
        }
        return minOccurrences
    }

    /**
     * nextState contains come pending obligations that must be fulfiled in order to construct a complete alignment.
     * Some of them correspond to executing some events from the remainder and do not incur additional cost.
     * It is, however, possible that some of them require executing an activity that cannot be aligned with anything in the remainder - this is the cost to estimate.
     */
    private fun minCostOfFulfillingAllPendingObligations(
        start: Int,
//        remainder: List<Event>,
        //aggregatedRemainder: Map<Event, Int>,
        nextState: CausalNetStateWithJoins,
        context: AlignmentContext,
        partialAlignment: Alignment,
        dec: DecoupledNodeExecution?
    ): Pair<Double, Map<Node, Int>> {
        //idea jest taka, że przepisujemy wszystko z poprzedniego, odejmujemy bieżącą decyzję i uwzględniamy nowy stan
        val minOccurrences = doSomeMagic(nextState)
        pushForward(minOccurrences)
        val current = if (dec != null) minOccurrences[dec.activity] else null
        for ((n, occ) in partialAlignment.minOccurrences) {
            minOccurrences.compute(n) { _, v ->
                if (v != null && v > occ)
                    return@compute v
                else
                    return@compute occ
            }
        }
        if (dec != null)
            minOccurrences.computeIfPresent(dec.activity) { _, v ->
                if (current == null || v > current) {
                    if (v > 1)
                        return@computeIfPresent v - 1
                    else
                        return@computeIfPresent null
                } else
                    return@computeIfPresent v
            }

        //logger.trace { "minOccurrences $minOccurrences for $nextState at $nextPosition" }
        /*if(minOccurrences[model.end]?:0 >= 2)   //executing end twice is unrecoverable
            return Double.POSITIVE_INFINITY*/
        /*
        var h2 = 0.0
//        val aggregatedRemainder = Counter<Event>()
//        for(e in remainder)
//            aggregatedRemainder.inc(e)
        for ((n, minOcc) in minOccurrences) {
            val skipDst = distance(n, null)
            val dsts = ArrayList<Double>()
            for((e, d) in context.cheapNodeAlignments[n].orEmpty()) {
                val ctr = aggregatedRemainder[e]?:0
                assert(d < skipDst)
                for(i in 0 until ctr)
                    dsts.add(d)
            }
            dsts.sort()
            var h = 0.0
            for (i in 0 until minOcc)
                h += if (i < dsts.size) min(skipDst, dsts[i]) else skipDst
            // logger.trace { "$n $minOcc -> $dsts -> $h" }
            h2 += h
        }
        return h2
         */
        var h2 = 0.0

        val dsts = ArrayList<Double>()

        for ((n, minOcc) in minOccurrences) {
            val skipDst = distance(n, null)
            dsts.clear()
//            for (e in remainder) {
            for (i in start until context.events.size) {
                val e = context.events[i]
                val d = distance(n, e)
                if (d < skipDst)
                    dsts.add(d)
            }
            if (dsts.size <= minOcc) {
                h2 += dsts.sum()
                h2 += (minOcc - dsts.size) * skipDst
            } else {
                dsts.sort()
                for (i in 0 until minOcc)
                    h2 += dsts[i]
            }
            /*
            dsts.sort()
            var h = 0.0
            for (i in 0 until minOcc)
                h += if (i < dsts.size) min(skipDst, dsts[i]) else skipDst
            // logger.trace { "$n $minOcc -> $dsts -> $h" }
            h2 += h
             */
        }
        return h2 to minOccurrences
    }


    private fun align(partialAlignment: Alignment, dec: DecoupledNodeExecution?, event: Event?): Alignment? {
        val newCost = partialAlignment.cost + distance(dec?.activity, event)
        logger.trace {
            "Aligning ${dec?.activity} with ${event?.conceptName} for ${partialAlignment.cost}+${
                distance(
                    dec?.activity,
                    event
                )
            } @$dec"
        }
        if (newCost < distance.maxAcceptableDistance) {
            val nextState = if (dec != null) {
                val nextState = CausalNetStateWithJoins(partialAlignment.state as CausalNetStateWithJoins)
                nextState.execute(dec.join, dec.split)
                nextState
            } else
                partialAlignment.state as CausalNetStateWithJoins
            val step = AlignmentStep(event, dec?.activity, partialAlignment.state)
            val nextAlignment = HierarchicalIterable(partialAlignment.alignment, step)
            val nextPosition = partialAlignment.tracePosition + (if (event != null) 1 else 0)
            val remainder =
                if (nextPosition < partialAlignment.context.events.size) partialAlignment.context.events.subList(
                    nextPosition,
                    partialAlignment.context.events.size
                ) else emptyList<Event>()
            val cheapRemainder =
                if (nextPosition < partialAlignment.context.events.size) partialAlignment.context.cheapEventAlignments.subList(
                    nextPosition,
                    partialAlignment.context.events.size
                ) else emptyList()
//            val aggregatedRemainder = if (nextPosition < partialAlignment.context.events.size)
//                partialAlignment.context.aggregatedRemainders[nextPosition]
//            else
//                emptyMap()

            val h1 = minCostOfEventAlignments(remainder, cheapRemainder, nextState, partialAlignment)
            val (h2, minOccurrences) = minCostOfFulfillingAllPendingObligations(
                nextPosition, nextState, partialAlignment.context,
                partialAlignment, dec
            )
            logger.trace { "h1=$h1 h2=$h2" }

            /*
            val heuristic = partialAlignment.state
                .mapToSet { it.target }
                .sumByDouble { n -> (remainder.map { e -> distance(n,e) } + listOf(distance(n, null))).min()?:0.0 }
             */
//            println("$heuristic ${partialAlignment.state} ${remainder.map {it.conceptName}}")
//            val nActivePossibleFutureActivities = nextState
//                .activeJoins
//                .mapToSet { it?.target }
//                .filterNotNull()
//                .map {  a->
//                    remainder
//                        .mapIndexed{idx,e -> idx to distance(a,e)}
//                        .filter { it.second == 0.0 }
//                        .minOfOrNull { it.first }
//                }
//                .filterNotNull()
//                .maxByOrNull { it }?:0
            val reachToTheFuture = dec?.split?.targets?.mapNotNull { a ->
                remainder
                    .mapIndexedNotNull { idx, e -> if (distance(a, e) == 0.0) idx else null }
                    .minOfOrNull { it }
            }
                ?.maxByOrNull { it } ?: 0
            val heuristic = h1 + h2
            //println("h1=$h1 h2=$h2")
            assert(partialAlignment.cost <= newCost) { "The cost is decreasing. Go figure." }
            /*
            if(!(dec?.activity == model.start || currentlyReachable.containsAll(nextReachable))) {
                val diff = nextReachable-currentlyReachable
                println("Reachable cache went bonkers @$dec")
                println("currentState=${partialAlignment.state}")
                println("nextState=${nextState}")
                println("currently=$currentlyReachable")
                println("next=$nextReachable")
                for(n in diff)
                    println("$n: ${model.joins[n]}")
                assert(false)
            }
             */
//            if (partialAlignment.cost + partialAlignment.heuristic > newCost + heuristic) {
//                val currentlyReachable = reachableCache[partialAlignment.state.uniqueSet()]
//                val nextReachable = reachableCache[nextState.uniqueSet()]
//                println("Position ${partialAlignment.tracePosition} -> $nextPosition")
//                println("Current state ${partialAlignment.state}")
//                println("Decision $dec")
//                println("Joins ${model.joins[dec?.activity]}")
//                println("Splits ${model.splits[dec?.activity]}")
//                println("Next state ${nextState}")
//                println("Min occ@current ${partialAlignment.minOccurrences}")
//                println("Min occ@next ${minOccurrences}")
////                println("Reachable from current state ${currentlyReachable}")
////                println("Reachable from next state ${nextReachable}")
////                println("next-currently=${nextReachable - currentlyReachable}")
//            }
            assert(partialAlignment.cost + partialAlignment.heuristic <= newCost + heuristic) { "The heuristic is not admissible: ${partialAlignment.cost}+${partialAlignment.heuristic}<=$newCost+$heuristic = $newCost + $h1 + $h2; dec=$dec nextState=$nextState remainder=${remainder.map { it.conceptName }}" }
//            if(nextPosition >= 46) {
//                println("newCost=$newCost h1=$h1 h2=$h2 ns=$nextState mo=$minOccurrences remainder=${remainder.map { it.conceptName }}")
//                TODO()
//            }
//            if (nextPosition >= 51)
//                println("\t$nextState h1=$h1 h2=$h2")
            //logger.trace{"newCost=$newCost h1=$h1 h2=$h2"}
            return Alignment(
                newCost,
                heuristic,
                nextAlignment,
                nextState,
                nextPosition,
                partialAlignment.context,
                reachToTheFuture,
                minOccurrences
            )
        }
        return null
    }

    private val stateContext = StateContext(model, HashMap())

    private fun initialAlignment(events: List<Event>): List<Alignment> {
        val nodes = model.instances.toList()
        val cheapEventAlignments = events.map { e ->
            val skipCost = distance(null, e)
            nodes.mapNotNull { n ->
                val d = distance(n, e)
                if (d < skipCost)
                    return@mapNotNull n to d
                else
                    return@mapNotNull null
            }
        }
        val cheapNodeAlignments = model.instances.associateWith { n ->
            val skipCost = distance(n, null)
            events.mapNotNull { e ->
                val d = distance(n, e)
                if (d < skipCost)
                    return@mapNotNull e to d
                else
                    return@mapNotNull null
            }
        }
        val context = AlignmentContext(model, events, distance, cheapEventAlignments, cheapNodeAlignments)
        return listOf(
            Alignment(
                0.0,
                0.0,
                emptyList<AlignmentStep>(),
                CausalNetStateWithJoins(stateContext),
                0,
                context,
                1,
                emptyMap()
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
                        ctr.compute(e.element.target) { _, v -> max(v ?: 0, e.count.toInt()) }
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
                    queue.add(
                        Alignment(
                            0.0,
                            0.0,
                            nextAlignment,
                            nextState,
                            nextPosition,
                            partialAlignment.context,
                            0,
                            emptyMap()
                        )
                    )
                }
            } else
                yield(partialAlignment)
        }
    }

    private fun modelReachedEnd(partialAlignment: Alignment): Boolean {
        if (partialAlignment.state.isEmpty() && partialAlignment.alignment.any { it.activity != null }) {
            val materialized = partialAlignment.alignment.toList()
            val nStarts = materialized.count { it.activity == model.start }
            val nEnds = materialized.count { it.activity == model.end }
            //println("nStart=$nStarts nEnds=$nEnds")
            return /*partialAlignment.state.isEmpty() && partialAlignment.alignment.any { it.activity != null } &&*/ nStarts == 1 && nEnds == 1
        }
        return false
    }

    private fun addMissingEnd(alignment: Alignment): Alignment {
        return alignment
    }

    private fun costCache() = HashMapWithDefault<AlignmentState, Double>(false) { Double.POSITIVE_INFINITY }

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

    private var callCtr = 0

    private val costLimitPerNode = 200

    data class AlignmentComputationResult(val alignment: Alignment?, val counter: Int, val totalCost: Double) {

    }

    /**
     * @return Alignment + number of visited states
     */
    internal fun computeOptimalAlignment(trace: Trace, acceptableCost: Int): AlignmentComputationResult =
        computeOptimalAlignment(trace, acceptableCost, Double.POSITIVE_INFINITY)
            ?: error("BUG: It was impossible to align the trace with the model.")

    internal fun computeOptimalAlignment(
        trace: Trace,
        acceptableCost: Int,
        maxAlignmentCost: Double
    ): AlignmentComputationResult? {
        reachableCache.clear()
        val traceLength = trace.events.count()
        logger.debug { "callctr=$callCtr" }
        callCtr++
        val bestCost = costCache()
        val events = trace.events.toList()
//        println(events.map{it.conceptName})
        val queue = PriorityQueue(AlignmentComparator(0))
//        val queue = MinMaxPriorityQueue.orderedBy(AlignmentComparator()).maximumSize(100).create<Alignment>()
        queue.addAll(initialAlignment(events))

        var ctr = 0
        var skipCtr = 0
        while (queue.isNotEmpty()) {
            val partialAlignment = queue.poll()
            /*if (bestCost[partialAlignment.alignmentState] <= partialAlignment.cost) {
                skipCtr++
                continue
            }
            if (partialAlignment.state.isNotEmpty())
                bestCost[partialAlignment.alignmentState] = partialAlignment.cost
             */
            ctr += 1
            if (ctr % 1000 == 0 /*|| partialAlignment.tracePosition >= 50*/)
                logger.debug {
                    "In progress visited $ctr skip $skipCtr queue ${queue.size} current cost ${partialAlignment.cost}+${partialAlignment.heuristic} position ${partialAlignment.tracePosition}/${partialAlignment.context.events.size} features=${partialAlignment.features} ${partialAlignment.state} ${
                        partialAlignment.context.events.subList(
                            partialAlignment.tracePosition,
                            partialAlignment.context.events.size
                        ).map { it.conceptName }
                    } ${(partialAlignment.state as CausalNetStateWithJoins).activeJoins}"
                }
            if (ctr > acceptableCost)
                return AlignmentComputationResult(null, ctr, partialAlignment.cost + partialAlignment.heuristic)
            val modelReachedEnd = modelReachedEnd(partialAlignment)
            val traceReachedEnd = partialAlignment.tracePosition >= events.size
            //logger.trace { "Queue size: ${queue.size}, cost: ${partialAlignment.cost}+${partialAlignment.heuristic} pos: ${partialAlignment.tracePosition} model ended $modelReachedEnd trace ended $traceReachedEnd ${partialAlignment.state} | " + partialAlignment.alignment.joinToString { "${it.event?.conceptName ?: '⊥'} -> ${it.activity ?: '⊥'}" } }
            logger.trace { "$ctr Queue size: ${queue.size}, cost: ${partialAlignment.cost}+${partialAlignment.heuristic} pos: ${partialAlignment.tracePosition} model ended $modelReachedEnd trace ended $traceReachedEnd ${partialAlignment.state} | " }
            if (modelReachedEnd && traceReachedEnd) {
                logger.trace("Visited $ctr partial alignments, skipctr=$skipCtr")
                //println("Visited $ctr cost ${partialAlignment.cost}+${partialAlignment.heuristic}")
                return AlignmentComputationResult(
                    addMissingEnd(partialAlignment),
                    ctr,
                    partialAlignment.cost + partialAlignment.heuristic
                )
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
                            if (bestCost[a.alignmentState] > a.cost && a.cost + a.heuristic <= maxAlignmentCost)
                                queue.add(a)
                        }
                    }
                }
                if (!match)
                    for (dec in decisions) {
                        val a = align(partialAlignment, dec, null)
                        if (a != null && bestCost[a.alignmentState] > a.cost && a.cost + a.heuristic <= maxAlignmentCost)
                            queue.add(a)
                    }
            }
            if (!match && !traceReachedEnd) {
                val a = align(partialAlignment, null, events[partialAlignment.tracePosition])
                if (a != null && bestCost[a.alignmentState] > a.cost && a.cost + a.heuristic <= maxAlignmentCost)
                    queue.add(a)
            }
        }
        return null
    }

    val perfectAlignments: List<Alignment?> by lazy {
        log.traces.mapIndexed { idx, trace ->
            val acceptableCost = Integer.MAX_VALUE //costLimitPerNode * max(trace.events.count(), model.instances.size)
            computeOptimalAlignment(trace, acceptableCost, 0.0)?.alignment
        }.toList()
    }

    internal val perfectFitRatio: Double by lazy {
        return@lazy perfectAlignments.sumOf { if (it != null) 1.0 else 0.0 } / perfectAlignments.size.toDouble()
    }

    val optimalAlignment: List<Alignment?> by lazy {
        log.traces.mapIndexed { idx, trace ->
            val acceptableCost = costLimitPerNode * max(trace.events.count(), model.instances.size)
            computeOptimalAlignment(trace, acceptableCost).alignment
        }.toList()
    }

    /**
     * Cost of the shortest valid sequence in the model
     */
    internal val movem: Double by lazy {
        val acceptableCost = costLimitPerNode * model.instances.size
        computeOptimalAlignment(Trace(emptySequence()), acceptableCost).totalCost
    }

    internal val ignoringModelCost: List<Double> by lazy {
        log.traces.map { trace -> trace.events.sumOf { distance(null, it) } }.toList()
    }

    internal val movel: Double by lazy {
        ignoringModelCost.sum()
    }

    val fcost: Double by lazy {
        var result = 0.0
        for ((idx, it) in optimalAlignment.withIndex()) {
            result += it?.cost ?: (ignoringModelCost[idx] + movem)
        }
        return@lazy result
    }

    val fitness: Double by lazy {
        1.0 - fcost / (movel + optimalAlignment.size * movem)
    }

    private val prefix2Step: Map<List<Activity>, List<AlignmentStep>> by lazy {
        val result = HashMap<List<Activity>, ArrayList<AlignmentStep>>()
        for (alignment in optimalAlignment) {
            if (alignment != null) {
                val prefix = ArrayList<Activity>()
                for (step in alignment.alignment) {
                    result.getOrPut(ArrayList(prefix)) { ArrayList() }.add(step)
                    if (step.activity != null)
                        prefix.add(step.activity)
                }
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
        val minSolutionLength = prefix.indices.map { i -> 1 + i + distanceToEnd[prefix[i]]!! }.maxOrNull()!!

        data class SearchState(
            val position: Int,
            val ctr: Int,
            val node: Activity?,
            val state: CausalNetState,
            val trace: Iterable<Node>
        ) : HasFeatures() {
            val length: Int
                get() = position + (if (node != null) 1 else 0)
            override val features: List<BigFraction>
                get() = listOf(
                    BigFraction(cost, 1),
                    //Fraction.getFraction(ctr, 1),
                    // if(length<prefix.size) Fraction.ZERO else Fraction.getFraction(-length, 1),
                    //if(length<prefix.size) Fraction.ZERO else Fraction.getFraction(state.size, 1)
                    BigFraction(stateSize, 1)
                )
            val cost: Int
            val stateSize: Int

            init {
                val targets = state.entrySet()
                    .groupBy({ it.element.target }, { it.copy() })
                    .mapValues { it.value.map { e -> e.count }.maxOrNull()!!.toInt() }
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
                }.minOrNull() ?: 0
                val b = targets.size
                val c = prefix.size - length
                val d = minSolutionLength - length
                val tmp = listOf(a, b, c, d)
                this.debug = tmp
                //logger.trace{"possible distances $tmp"}
                return tmp.maxOrNull()!!
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
                        if (newState.countSet().any { it >= 2 })
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
        val maxLen = 2 * (log.traces.map { it.events.count() }.maxOrNull() ?: 50)   //as good a default as any?
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
        val seqs = hell.computeSetOfValidSequences(false) { seq, _ ->
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

    internal fun possibleNextOnPartialAlignments(prefixes: Collection<List<Activity>>): Map<List<Activity>, Set<Activity>> {
        data class SearchState(
            val position: Int,
            val produce: Boolean,
            val relevantPrefixes: Trie<Activity>,
            val state: CausalNetStateWithJoins
        ) {
            val pending: Int by lazy {
                val active = this.state.activeJoins.filterNotNull().mapToSet { it.target }
                try {
                    return@lazy this
                        .relevantPrefixes
                        .maxOf { it.subList(this.relevantPrefixes.prefix.size, it.size).toSet().intersect(active).size }
                } catch (e: NoSuchElementException) {
                    return@lazy 0
                }
            }
        }

        val maxPartialAlignments = 1
        val result = HashMap<List<Activity>, HashSet<Activity>>()
        result[emptyList<Activity>()] = hashSetOf<Activity>(model.start)
        for (prefix in prefixes) {
            if (prefix.isNotEmpty() && prefix.last() == model.end)
                result[prefix] = hashSetOf<Activity>(model.end)   //FIXME huh wtf?
        }
        /*
        // number of visited states, nasa, first 30 traces
         *  9824 b.state.size - a.state.size
         *  9909 b.position - a.position b.state.size - a.state.size
         *  9276 b.position - a.position b.produce-a.produce b.state.size - a.state.size
         *  9279 b.produce-a.produce b.position - a.position b.state.size - a.state.size
         *  9786 b.state.size - a.state.size b.position - a.position b.produce-a.produce
         *  9675 b.position - a.position b.produce-a.produce b.state.activeJoins.size - a.state.activeJoins.size
         *  9442 b.position - a.position b.produce-a.produce b.state.size-a.state.size b.state.activeJoins.size - a.state.activeJoins.size
         *  >359k b.position - a.position b.produce-a.produce b.state.size-a.state.size a.state.activeJoins.size - b.state.activeJoins.size
         *  9026 b.position - a.position b.produce-a.produce b.state.size - a.state.size b.pending-a.pending
         *  9589 b.position - a.position b.produce-a.produce b.pending-a.pending b.state.size - a.state.size
         *  9521 b.position - a.position b.produce-a.produce b.state.size - a.state.size a.pending-b.pending
         *  >90k b.position - a.position b.produce-a.produce b.pending-a.pending
         ================ 0 until 90
         *  486186 b.position - a.position b.produce-a.produce b.state.size - a.state.size b.pending-a.pending
         *  544910 b.position - a.position b.produce-a.produce b.state.size - a.state.size
         *  520770 b.position - a.position b.produce-a.produce b.pending-a.pending b.state.size - a.state.size
         */
        val queue = PriorityQueue<SearchState>(kotlin.Comparator { a, b ->
            val ap = 2 * a.position + (if (a.produce) 1 else 0)
            val bp = 2 * b.position + (if (b.produce) 1 else 0)
            if (ap != bp)
                return@Comparator bp - ap
            if (a.state.size != b.state.size)
                return@Comparator b.state.size - a.state.size
            return@Comparator b.pending - a.pending
        })
        var ctr = 0
        val nonEmptyPrefixes = prefixes - result.keys

        if (nonEmptyPrefixes.isNotEmpty()) {
            val missing = Trie.build<Activity>(nonEmptyPrefixes.toMutableSet())
            if (nonEmptyPrefixes.isNotEmpty()) {
                queue.add(
                    SearchState(
                        0,
                        true,
                        missing,
                        CausalNetStateWithJoins(StateContext(model, HashMap()))
                    )
                )
                val maximalSplits = HashMap<Activity, List<Split>>()
                while (missing.isNotEmpty() && queue.size > 0) {
                    val current = queue.poll()
                    if (!current.relevantPrefixes.containsAny(missing))
                        continue

//                println("${current.position} ${current.produce} ${current.state.uniqueSet().size} ${current.state.size} ${current.pending} -> $ctr")
                    ctr++

                    if (current.produce) {
                        var hasSatPrefix = false
                        for (prefix in current.relevantPrefixes) {
                            // dla kazdego node w przyszłości musi być chociaż jeden join, dla którego wszystkie zależności albo już są albo da się je wyprodukować
                            // wystarczy, że znajdziemy chociaż jeden taki prefix w relevantPrefixes&missing
                            if (current.position < prefix.size && prefix in missing) {
                                val available = current.state.uniqueSet().toHashSet()
                                available.addAll(model.outgoing[prefix[current.position]].orEmpty())
                                var unsatPrefix = false
                                for (pos in current.position + 1 until prefix.size) {
                                    val node = prefix[pos]
                                    if (!model.joins[node].orEmpty().any { available.containsAll(it.dependencies) }) {
                                        unsatPrefix = true
                                        break
                                    }
                                    available.addAll(model.outgoing[node].orEmpty())
                                }
                                if (!unsatPrefix) {
                                    hasSatPrefix = true
                                    break
                                }
                            }
                        }

                        if (!hasSatPrefix)
                            continue
                    }

                    if (current.produce) {
                        val currentNode = current.relevantPrefixes.node
                        check(currentNode != null)
                        val splits = maximalSplits.computeIfAbsent(currentNode) {
                            val splits = model.splits[currentNode].orEmpty()
                            return@computeIfAbsent splits.filter { split ->
                                splits.all { other ->
                                    split === other || !other.dependencies.containsAll(split.dependencies)
                                }
                            }
                        }

                        for (split in splits) {
                            val nextState = CausalNetStateWithJoins(current.state)
                            nextState.execute(null, split)
                            val keys = hashSetOf<Activity?>(null)
                            nextState.activeJoins.filterNotNull().mapTo(keys) { it.target }
                            val relevantChildren = current
                                .relevantPrefixes
                                .children
                                .filterKeys { it in keys }
                                .toMutableMap()
                            val relevant = Trie(
                                current.relevantPrefixes.node,
                                current.relevantPrefixes.prefix,
                                relevantChildren
                            )
                            queue.add(SearchState(current.position + 1, false, relevant, nextState))
                        }
                    } else {
                        for ((candidate, activeJoins) in current.state.activeJoins.filterNotNull()
                            .groupBy { it.target }) {
                            val prefixSoFar = current.relevantPrefixes.prefix
                            result.computeIfAbsent(prefixSoFar) { HashSet() }.add(candidate)
                            if (missing.remove(prefixSoFar))
                                logger.debug { "#missing ${missing.size}/${nonEmptyPrefixes.size} #solved prefix ${prefixSoFar.size} #queue ${queue.size} #ctr $ctr" }
                            val relevant = current.relevantPrefixes.children[candidate]
                            if (relevant != null) {
                                val smallestJoinSize = activeJoins.minOf { it.size }
                                val smallestJoins = activeJoins.filter { it.size == smallestJoinSize }
                                for (join in smallestJoins) {
                                    val nextState = CausalNetStateWithJoins(current.state)
                                    nextState.execute(join, null)
                                    queue.add(SearchState(current.position, true, relevant, nextState))
                                }
                            }
                        }
                    }
                }
            }
//        if (missing.size != 0) {
//            for(m in missing)
//                println(m)
//        }
//        assert(missing.size == 0)
//        println("#ctr $ctr")
            //println(result)
        }
        return result.filterKeys { it in prefixes }
    }


    private fun pnew(w: Int, n: Int): Double =
        if (n >= w + 2) (w * (w + 1)).toDouble() / (n * (n - 1)).toDouble() else 1.0

    private val generalization: Pair<Double, Double> by lazy {
        val state2Event = Counter<CausalNetState>()
        val state2Activity = HashMap<CausalNetState, HashSet<Activity>>()
        for (alignment in optimalAlignment) {
            if (alignment != null) {
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
            if (alignment != null) {
                val prefix = ArrayList<Activity>()
                for (step in alignment.alignment) {
                    if (step.activity != null) {
                        var observed = prefix2Next.getValue(prefix)
                        val possible = if (prefix.isNotEmpty()) prefix2Possible[prefix] else setOf(model.start)
                        logger().trace { "observed=$observed possible=$possible" }
                        if (possible == null) {
                            logger.warn("Nothing is possible in $prefix")
                            continue
                        }
                        if (!possible.containsAll(observed)) {
                            logger.warn("For $prefix there are activities that were observed, but are not possible: ${observed - possible}")
                            observed = observed.filter { it in possible }
                        }
                        result += observed.size.toDouble() / possible.size.toDouble()
                        ctr += 1
                        prefix.add(step.activity)
                    }
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

