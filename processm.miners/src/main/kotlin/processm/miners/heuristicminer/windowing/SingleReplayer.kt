package processm.miners.heuristicminer.windowing

import com.google.common.collect.MinMaxPriorityQueue
import processm.core.helpers.HierarchicalIterable
import processm.core.helpers.allSubsets
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.models.causalnet.*
import processm.miners.heuristicminer.NodeTrace
import processm.miners.heuristicminer.ReplayTrace
import processm.miners.heuristicminer.bindingproviders.LazyCausalNetState
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

    private val hypothesis41 = false

    private fun hypothesis41(current: SearchState): Boolean {
        val joins = current.trace.joins.toList()
        val splits = current.trace.splits.toList()
        val trace = current.nodeTrace
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


    fun replay(model: CausalNet, trace: NodeTrace): Pair<List<Split>, List<Join>> {
        val producible = trace.indices.map { idx ->
            val end = if (horizon > 0) min(idx + 1 + horizon, trace.size) else trace.size
            model.outgoing[trace[idx]].orEmpty()
                .intersect(trace.subList(idx + 1, end).map { Dependency(trace[idx], it) })
        }
        logger.debug("$trace")
        val remainder = trace.indices.map { idx -> trace.subList(idx + 1, trace.size).groupingBy { it }.eachCount() }
        var maxSize = 10   //it seems that it is actually better to start with a small queue, because the cost of queue management is non-negligible
        var ctr = 0
        while (maxSize <= 1e9) { //TODO make this a parameter
            maxSize *= 10
            logger.debug { "maxSize=$maxSize" }
            val queue = MinMaxPriorityQueue.maximumSize(maxSize).create<SearchState>()
            val seen = HashSet<Pair<CausalNetState, Int>>()
            queue.add(
                SearchState(
                    0.0,
                    0,
                    0,
                    true,
                    ReplayTrace(CausalNetStateImpl(), listOf(), listOf()),
                    trace
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
                ctr++
                if (ctr % 10000 == 0)
                    logger.debug { "ctr=$ctr efficiency=${ctr / (2.0 * trace.size - 1)} ${current.features} ${current.trace.state.entrySet()}" }
                val currentNode = trace[current.node]
//            logger.trace { queue.map { it.features }.toString()}
                logger.trace { "$currentNode ${current.node}/${current.produce}: ${current.features} ${current.trace.state.entrySet()} $trace" }
                if (current.produce && current.node == trace.size - 1) {
                    if (current.trace.state.isEmpty()) {
                        logger.debug { "FINAL ctr=$ctr efficiency=${ctr / (2.0 * trace.size - 1)} ${current.features}" }
//                        check(hypothesis41(current))
                        return current.trace.splits.map { Split(it.toSet()) } to current.trace.joins.map { Join(it.toSet()) }
                    } else {
                        //this is an invalid solution without any chances of improvement
                        continue
                    }
                }
                val newLength = current.solutionLength + 1
                if (current.produce) {
                    //cannotProduce contains dependencies that are currently full of tokens and adding any to it would yield a dead-end in search
                    val cannotProduce = HashSet<Dependency>()
                    var isNextRunnable = false
                    val depToNext = Dependency(
                        currentNode,
                        trace[current.node + 1]
                    )  //DF-completness guarantees presence of this dependency. It also guarantees that we don't need to look any further, as any immediate successor is runnable by its direct predecessor
                    for (e in current.trace.state.entrySet()) {
                        if (e.element.source == currentNode && e.count >= remainder[current.node][e.element.target] ?: 0) {
                            cannotProduce.add(e.element)
                        }
                        if (!isNextRunnable && e.element == depToNext)
                            isNextRunnable = true
                    }

                    val mustProduce = if (hypothesis41 || !isNextRunnable) setOf(depToNext) else emptySet()
                    if (mustProduce.isNotEmpty())
                        logger.trace { "Must produce $mustProduce" }
                    if (cannotProduce.isNotEmpty())
                        logger.trace { "Cannot produce $cannotProduce; available ${producible[current.node] - cannotProduce}" }

                    //-------
                    if (hypothesis41) {
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
                            continue
                    } else {
                        if (cannotProduce.contains(Dependency(trace[trace.size - 2], trace[trace.size - 1]))) {
                            logger.debug("Cannot produce contains the last dependency which, by definition, must be executed")
                            continue
                        }
                        if (mustProduce.any { cannotProduce.contains(it) }) {
                            logger.warn("Unexpected clash, mustProduce $mustProduce cannotProduce $cannotProduce")
                            continue
                        }
                    }
                    //---------
                    val tmp = producible[current.node] - cannotProduce - mustProduce
                    val prod :List<Set<Dependency>> = if(tmp.isNotEmpty())
                        tmp.allSubsets(excludeEmpty = mustProduce.isEmpty())
                    else {
                        if(mustProduce.isNotEmpty())
                            listOf(emptySet())
                        else
                            emptyList()
                    }
                    logger.trace{"prod.size=${prod.size} queue.size=${queue.size}"}

                    for (mayProduce in prod) {
                        val produce = mayProduce + mustProduce
                        val newValue = current.totalGreediness + produce.size.toDouble() / producible[current.node].size
                        val newReplayTrace =
                            ReplayTrace(
                                LazyCausalNetState(current.trace.state, emptyList(), produce),
                                current.trace.joins,
                                HierarchicalIterable(current.trace.splits, produce)
                            )
                        queue.add(
                            SearchState(
                                newValue,
                                newLength,
                                current.node + 1,
                                false,
                                newReplayTrace,
                                trace
                            )
                        )
                    }
                    logger.trace{"queue.size=${queue.size}"}
                } else {
                    val avail = remainder[current.node][currentNode] ?: 0
                    //mustConsume contains dependencies that must be consumed here otherwise the resulting state will be a dead end
                    val mustConsume = HashSet<Dependency>()
                    for (e in current.trace.state.entrySet()) {
                        if (e.element.target == currentNode && e.count > avail) {
                            mustConsume.add(e.element)
                        }
                    }
                    if (mustConsume.isNotEmpty())
                        logger.trace { "mustConsume $mustConsume" }
                    val allConsumable = current.trace.state.uniqueSet().intersect(model.incoming.getValue(currentNode))
                    val consumable = allConsumable - mustConsume
                    for (mayConsume in consumable.allSubsets(excludeEmpty = mustConsume.isEmpty())) {
                        val consume = mayConsume + mustConsume
                        assert(consume.isNotEmpty())
                        val newValue = current.totalGreediness + consume.size.toDouble() / allConsumable.size
                        val newReplayTrace =
                            ReplayTrace(
                                LazyCausalNetState(current.trace.state, consume, emptyList()),
                                HierarchicalIterable(current.trace.joins, consume),
                                current.trace.splits
                            )
                        val newSearchState = SearchState(
                            newValue,
                            newLength,
                            current.node,
                            true,
                            newReplayTrace,
                            trace
                        )
                        queue.add(newSearchState)
                    }
                }
            }
        }
        throw IllegalStateException()
    }

    override fun replayGroup(model: CausalNet, traces: List<NodeTrace>): Pair<Set<Split>, Set<Join>> {
        val splits = HashSet<Split>()
        val joins = HashSet<Join>()
        for ((idx, trace) in traces.withIndex()) {
            logger.debug("$idx/${traces.size}")
            val (tmpsplits, tmpjoins) = replay(model, trace)
            logger.debug { "$tmpsplits" }
            logger.debug { "$tmpjoins" }
            splits.addAll(tmpsplits)
            joins.addAll(tmpjoins)
        }
        return splits to joins
    }
}