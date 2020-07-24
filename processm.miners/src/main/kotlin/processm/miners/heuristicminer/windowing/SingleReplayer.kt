package processm.miners.heuristicminer.windowing

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.MinMaxPriorityQueue
import org.apache.commons.collections4.multiset.HashMultiSet
import processm.core.helpers.Counter
import processm.core.helpers.HierarchicalIterable
import processm.core.helpers.allSubsets
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.models.causalnet.*
import processm.miners.heuristicminer.HashMapWithDefault
import processm.miners.heuristicminer.NodeTrace
import processm.miners.heuristicminer.ReplayTrace
import processm.miners.heuristicminer.bindingproviders.LazyCausalNetState
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.min

class SingleReplayer(val horizon: Int = -1) : Replayer {

    companion object {
        private val logger = logger()
    }

    private fun canConsumeAllActiveDependencies(remainder: Map<Node, Int>, state: CausalNetState): Boolean {
        for (e in state.entrySet()) {
            val avail = remainder[e.element.target] ?: 0
            if (avail < e.count) {
                // there are more tokens on some dependency than it will be possible to consume
                return false
            }
        }
        return true
    }

    private val alwaysUseDirectlyFollows = false

    private fun hypothesis41(trace: NodeTrace, current: SearchState): Boolean {
        val joins = current.trace.joins.toList()
        val splits = current.trace.splits.toList()
        println("$trace")
        println("joins=$joins")
        println("splits=$splits")
        for ((i, a) in trace.withIndex()) {
            /*
            println("a=$a joins[$i]=${joins[i]} splits[$i]=${splits[i]}")
             */
            if (i > 0 && !joins[i - 1].contains(Dependency(trace[i - 1], a))) {
                return false
            }
            if (i < trace.size - 1 && !splits[i].contains(Dependency(a, trace[i + 1])))
                return false
        }
        return true
    }

    private data class Context(
        val model: CausalNet,
        val remainder: List<Map<Node, Int>>,
        val producible: List<Set<Dependency>>,
        val trace: NodeTrace,
        val queue: AbstractQueue<SearchState>
    ) {
        val largestPossibleSplit = producible.indices.map{ node -> producible.subList(node, producible.size).map { it.size }.max()!!}
    }

    private fun consumption(current: SearchState, context: Context) {
        val currentNode = context.trace[current.node]
        val avail = context.remainder[current.node][currentNode] ?: 0
        //mustConsume contains dependencies that must be consumed here otherwise the resulting state will be a dead end
        val mustConsume = HashSet<Dependency>()
        for (e in current.trace.state.entrySet()) {
            if (e.element.target == currentNode && e.count > avail) {
                mustConsume.add(e.element)
            }
        }
        if (mustConsume.isNotEmpty())
            logger.trace { "mustConsume $mustConsume" }
        val allConsumable = current.trace.state.uniqueSet().intersect(context.model.incoming.getValue(currentNode))
        val consumable = allConsumable - mustConsume
        for (mayConsume in consumable.allSubsets(excludeEmpty = mustConsume.isEmpty())) {
            val consume = mayConsume + mustConsume
            assert(consume.isNotEmpty())
            val additionalGain = consume.size.toDouble() / allConsumable.size
            val newValue = current.totalGreediness + additionalGain
            val newReplayTrace =
                ReplayTrace(
                    LazyCausalNetState(current.trace.state, consume, emptyList()),
                    HierarchicalIterable(current.trace.joins, consume),
                    current.trace.splits
                )
            val penalty = relaxedHeuristic(newReplayTrace.state, current.node, true, context)
            val newSearchState = SearchState(
                newValue,
                penalty,
                current.solutionLength + 1,
                current.node,
                true,
                newReplayTrace
            )
            context.queue.add(newSearchState)
        }
    }

    private fun cannotProduceJoinly(current: SearchState, context: Context): Set<Dependency> {
        val trace = context.trace
        val currentNode = trace[current.node]
        //**************************
        // Each node in the future must have at least one dependency runnable
        // If the nearest future occurrence of current node has no runnable dependencies, then this state is invalid
        // For this to happen none of already active dependencies concerning this node must be consumable between current and next occurrence's cannotProduce must be full
        //**************************
        var next = -1
        for (i in current.node + 1 until trace.size)
            if (trace[i] == currentNode) {
                next = i
                break
            }
        if (next < 0) // no further occurrences of the current node
            return emptySet()
        val between = HashSet(trace.subList(current.node + 1, next + 1))
        logger.trace { "between=$between outgoing=${context.model.outgoing.getValue(currentNode).map { it.target }}" }
        val anyConsumable = context.producible[next].any { dep -> between.contains(dep.target) }
        if (anyConsumable)   // there is a potential for recovery
            return emptySet<Dependency>()
        // Identify any relevant dependencies that would become full. They cannnot be produced jointly.
        val willBecomeFull = HashSet<Dependency>()
        for (e in current.trace.state.entrySet()) {
            if (context.producible[current.node].contains(e.element) && e.count + 1 >= context.remainder[current.node][e.element.target] ?: 0)
                willBecomeFull.add(e.element)
        }
        return willBecomeFull
    }

    private fun production(current: SearchState, context: Context) {
        val currentNode = context.trace[current.node]
        val trace = context.trace
        val lastDep = Dependency(trace[trace.size - 2], trace[trace.size - 1])
        //cannotProduce contains dependencies that are currently full of tokens and adding any to it would yield a dead-end in search
        val cannotProduce = HashSet<Dependency>()
        var isNextRunnable = false
        //DF-completness guarantees presence of this dependency. It also guarantees that we don't need to look any further, as any immediate successor is runnable by its direct predecessor
        val depToNext = Dependency(currentNode, trace[current.node + 1])
        for (e in current.trace.state.entrySet()) {
            if (e.element.source == currentNode && e.count >= context.remainder[current.node][e.element.target] ?: 0) {
                cannotProduce.add(e.element)
            }
            if (!isNextRunnable && e.element == depToNext)
                isNextRunnable = true
        }

        val mustProduce = if (alwaysUseDirectlyFollows || !isNextRunnable) setOf(depToNext) else emptySet()

        if (mustProduce.isNotEmpty() || cannotProduce.isNotEmpty())
            logger.trace { "Must produce $mustProduce cannot produce $cannotProduce" }
        if (cannotProduce.isNotEmpty())
            logger.trace { "Cannot produce $cannotProduce; available ${context.producible[current.node] - cannotProduce}" }

        //-------
        if (alwaysUseDirectlyFollows) {
            var futureClash = false
            val df = HashSet<Node>()
            for (i in current.node until trace.size - 1) {
                val b = trace[i + 1]
                if (!df.contains(b)) {
                    val dep = Dependency(trace[i], b)
                    if (cannotProduce.contains(dep)) {
                        logger.trace("cannot produce $dep but it will be surely produced in the future, aborting")
                        futureClash = true
                        break
                    }
                    df.add(b)
                }
            }
            if (futureClash)
                return
        } else {

            if (current.node != trace.size - 2) {   //if we are at not the second to last node, we cannot produce last dependency
                cannotProduce.add(lastDep)
            } else if (cannotProduce.contains(lastDep)) { //otherwise we must produce it
                logger.debug("Cannot produce contains the last dependency which, by definition, must be executed")
                return
            }

            if (mustProduce.any { cannotProduce.contains(it) }) {
                logger.warn("Unexpected clash, mustProduce $mustProduce cannotProduce $cannotProduce")
                return
            }
        }
        //---------
        val tmp = context.producible[current.node] - cannotProduce - mustProduce
        val prod: List<Set<Dependency>> = if (tmp.isNotEmpty())
            tmp.allSubsets(excludeEmpty = mustProduce.isEmpty())
        else {
            if (mustProduce.isNotEmpty())
                listOf(emptySet())
            else
                emptyList()
        }

        if (prod.isEmpty())
            logger.trace("Cannot produce anything")

        val cannotProduceJointly = cannotProduceJoinly(current, context)

        if (cannotProduceJointly.isNotEmpty())
            logger.trace { "Cannot produce jointly $cannotProduceJointly" }

        for (mayProduce in prod) {
            val produce = mayProduce + mustProduce

            if (cannotProduceJointly.isNotEmpty() && produce.containsAll(cannotProduceJointly)) {
                logger.trace { "Skipping production $produce" }
                continue
            }

            val newValue = current.totalGreediness + produce.size.toDouble() / context.producible[current.node].size
            val newReplayTrace =
                ReplayTrace(
                    LazyCausalNetState(current.trace.state, emptyList(), produce),
                    current.trace.joins,
                    HierarchicalIterable(current.trace.splits, produce)
                )
            val penalty = relaxedHeuristic(newReplayTrace.state, current.node+1, false, context)
            context.queue.add(
                SearchState(
                    newValue,
                    penalty,
                    current.solutionLength + 1,
                    current.node + 1,
                    false,
                    newReplayTrace
                )
            )
        }
    }

    private fun relaxedHeuristicSlower(initState:CausalNetState, node:Int, produce: Boolean, context: Context):Double {
        // Optimum produkcji to uruchomienie wszystkich producible i zjedzenie wszystkiego co sie da.
        // To zostawi mi jakies tokeny w sieci i kazdy token jest stratą, którą koniecznie musimy ponieść
        val n2int = HashMap<Node, Int>()
        val intTrace = ArrayList<Int>()
        var ctr=0
        for(pos in node until context.trace.size)
            intTrace.add(n2int.getOrPut(context.trace[pos]) {ctr++})
        for(e in initState.entrySet())
            n2int.getOrPut(e.element.source) {ctr++}
        val state = List(ctr) { MutableList(ctr) {0} }
        var flatStateSize= 0
        for(e in initState.entrySet()) {
            val t=n2int[e.element.target]!!
            val s=n2int[e.element.source]!!
            state[t][s] = e.count
            flatStateSize += e.count
        }
        for((pos, currentNode) in intTrace.withIndex()) {
            if(!produce || pos>0) {
                for(i in state[currentNode].indices)
                    if(state[currentNode][i]>=1) {
                        state[currentNode][i]--
                        flatStateSize--
                    }
            }
//            logger.trace{"pos=$pos node=$node intTrace=$intTrace trace=${context.trace}"}
            for(p in context.producible[pos+node]) {
                state[n2int[p.target]!!][currentNode]++
                flatStateSize++
            }
        }
        val penalty = if(flatStateSize>0) flatStateSize.toDouble()/context.largestPossibleSplit[node] else 0.0
        logger.trace{"penalty $penalty flat state size $flatStateSize remaining after stuffing myself full $state"}
        return penalty
    }

    private fun relaxedHeuristic(initState:CausalNetState, node:Int, produce: Boolean, context: Context):Double {
        // Optimum produkcji to uruchomienie wszystkich producible i zjedzenie wszystkiego co sie da.
        // To zostawi mi jakies tokeny w sieci i kazdy token jest stratą, którą koniecznie musimy ponieść
        val state = HashMapWithDefault<Node, Counter<Dependency>>() {Counter<Dependency>()}
        for(e in initState.entrySet())
            state[e.element.target][e.element] = e.count
        for(pos in node until context.trace.size) {
            val currentNode = context.trace[pos]
            if(!produce || pos!=node) {
                for(e in state[currentNode].entries)
                    if(e.value>=1)
                        e.setValue(e.value-1)
            }
            for(p in context.producible[pos])
                state[p.target].inc(p)
        }
        val flatStateSize = state.values.sumBy { it.values.sum() }
        val penalty = if(flatStateSize>0) flatStateSize.toDouble()/context.largestPossibleSplit[node] else 0.0
        logger.trace{"penalty $penalty remaining after stuffing myself full $state"}
        return penalty
    }

    /**
     * Number of states visited during the last call to [replay]
     */
    var visitedStates: Int = 0
        private set

    /**
     * Minimal number of states visited during the last call to [replay]
     */
    var minimalVisitedStates: Int = 0
        private set

    /**
     * Efficiency of the last call to [replay], measured as the number of visitied states to the minimal number of visited states
     */
    val efficiency: Double
        get() = visitedStates.toDouble() / minimalVisitedStates

    /**
     * Efficiency for each trace during the last call of [replayGroup]
     */
    var groupEfficiency: List<Double> = emptyList()
        internal set


    fun replay(model: CausalNet, trace: NodeTrace): Pair<List<Split>, List<Join>> {
        val producible = trace.indices.map { idx ->
            val end = if (horizon > 0) min(idx + 1 + horizon, trace.size) else trace.size
            model.outgoing[trace[idx]].orEmpty()
                .intersect(trace.subList(idx + 1, end).map { Dependency(trace[idx], it) })
        }
        minimalVisitedStates = 2 * trace.size - 1
        logger.debug("$trace")
        val remainder = trace.indices.map { idx -> trace.subList(idx + 1, trace.size).groupingBy { it }.eachCount() }
        var maxSize =
            10   //it seems that it is actually better to start with a small queue, because the cost of queue management is non-negligible
        visitedStates = 0
        while (maxSize <= 1e9) { //TODO make this a parameter
            maxSize *= 10
            logger.debug { "maxSize=$maxSize" }
            val queue = MinMaxPriorityQueue.maximumSize(maxSize).create<SearchState>()
            val context = Context(model, remainder, producible, trace, queue)
            val seen = HashSet<Pair<CausalNetState, Int>>()
            queue.add(
                SearchState(
                    0.0,
                    0.0,
                    0,
                    0,
                    true,
                    ReplayTrace(CausalNetStateImpl(), listOf(), listOf())
                )
            )
            while (!queue.isEmpty()) {
                val current = queue.pollFirst()
                val key = current.trace.state to current.solutionLength
                if (seen.contains(key)) {
//                    logger.trace("Skipping state at ${current.solutionLength} was seen at ${current}")
                    continue
                }
                seen.add(key)
                visitedStates++
                if (visitedStates % 10000 == 0)
                    logger.debug { "ctr=${visitedStates} efficiency=$efficiency ${current.features} ${current.trace.state.entrySet()}" }
                val currentNode = trace[current.node]
//            logger.trace { queue.map { it.features }.toString()}
                logger.trace { "$currentNode ${current.node}/${current.produce}: ${current.features} ${current.trace.state.entrySet()} $trace" }
                if (current.produce && current.node == trace.size - 1) {
                    if (current.trace.state.isEmpty()) {
                        logger.debug { "FINAL ctr=${visitedStates} efficiency=$efficiency ${current.features}" }
//                        check(hypothesis41(current))
                        return current.trace.splits.map { Split(it.toSet()) } to current.trace.joins.map { Join(it.toSet()) }
                    } else {
                        //this is an invalid solution without any chances of improvement
                        continue
                    }
                }
                if (current.produce) {
                    production(current, context)
                } else {
                    consumption(current, context)
                }
            }
        }
        throw IllegalStateException()
    }

    override fun replayGroup(model: CausalNet, traces: List<NodeTrace>): Pair<Set<Split>, Set<Join>> {
        val splits = HashSet<Split>()
        val joins = HashSet<Join>()
        var eff = ArrayList<Double>()
        for ((idx, trace) in traces.withIndex()) {
            logger.debug("$idx/${traces.size}")
            val (tmpsplits, tmpjoins) = replay(model, trace)
            logger.debug { "$tmpsplits" }
            logger.debug { "$tmpjoins" }
            splits.addAll(tmpsplits)
            joins.addAll(tmpjoins)
            eff.add(efficiency)
        }
        groupEfficiency = eff
        return splits to joins
    }
}