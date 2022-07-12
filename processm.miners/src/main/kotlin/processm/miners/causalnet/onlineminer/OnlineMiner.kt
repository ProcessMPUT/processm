package processm.miners.causalnet.onlineminer

import org.apache.commons.collections4.multiset.HashMultiSet
import processm.core.log.hierarchical.Log
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.miners.causalnet.CausalNetMiner
import processm.miners.causalnet.onlineminer.replayer.Replayer
import processm.miners.causalnet.onlineminer.replayer.SingleReplayer

/**
 * Online miner, generates a valid causal net perfectly fitting the current window, as given by either [processLog] or [processDiff]
 */
class OnlineMiner(
    val replayer: Replayer = SingleReplayer(),
    val traceToNodeTrace: TraceToNodeTrace = BasicTraceToNodeTrace()
) : CausalNetMiner {

    companion object {
        private val logger = logger()
    }

    private lateinit var model: MutableCausalNet
    override val result: MutableCausalNet
        get() = model
    private val window = HashMultiSet<NodeTrace>()
    private val directlyFollows = object : HashMap<Dependency, Int>() {
        override operator fun get(key: Dependency): Int = super.get(key) ?: 0

        fun inc(key: Dependency, ctr: Int) = this.compute(key) { _, v ->
            val nv = (v ?: 0) + ctr
            check(nv >= 0)
            return@compute nv
        }
    }
    private val start = Node("start", isSilent = true)
    private val end = Node("end", isSilent = true)

    private fun aggregateLog(log: Log): HashMultiSet<NodeTrace> {
        val result = HashMultiSet<NodeTrace>()
        log.traces.mapTo(result) { traceToNodeTrace(it) }
        return result
    }

    private fun updateDirectlyFollows(nodeTrace: NodeTrace, ctr: Int) {
        val i = nodeTrace.iterator()
        var prev = start
        while (i.hasNext()) {
            val curr = i.next()
            directlyFollows.inc(Dependency(prev, curr), ctr)
            prev = curr
        }
        directlyFollows.inc(Dependency(prev, end), ctr)
    }

    /**
     * Modifies the underlying window by adding traces in [addLog] and removing traces from [removeLog].
     * The model in [result] is updated so that it perfectly fits the current window.
     */
    fun processDiff(addLog: Log, removeLog: Log) {
        val addTraces = aggregateLog(addLog)
        val removeTraces = aggregateLog(removeLog)
        val newlyAdded = HashSet<NodeTrace>()
        val freshlyRemoved = HashSet<NodeTrace>()
        for (remove in removeTraces.entrySet()) {
            if (window.remove(remove.element, remove.count) == remove.count)
                freshlyRemoved.add(remove.element)
            updateDirectlyFollows(remove.element, -remove.count)
        }
        for (add in addTraces.entrySet()) {
            if (window.add(add.element, add.count) == 0)
                newlyAdded.add(add.element)
            updateDirectlyFollows(add.element, add.count)
        }
        assert(addTraces.uniqueSet().all { trace ->
            (0 until trace.size - 1).all { i -> directlyFollows[Dependency(trace[i], trace[i + 1])] >= 1 }
        }) { "Directly follows is inconsistent with new traces" }
        assert(window.uniqueSet().all { trace ->
            (0 until trace.size - 1).all { i -> directlyFollows[Dependency(trace[i], trace[i + 1])] >= 1 }
        }) { "Directly follows is inconsistent with the window" }

        val removedAndAdded = newlyAdded.intersect(freshlyRemoved)
        newlyAdded.removeAll(removedAndAdded)
        freshlyRemoved.removeAll(removedAndAdded)
        val touchedActivities = HashSet<Node>()
        newlyAdded.flatMapTo(touchedActivities) { it }
        freshlyRemoved.flatMapTo(touchedActivities) { it }

        val splits = HashSet<Split>()
        val joins = HashSet<Join>()
        if (this::model.isInitialized) {
            val untouchedActivities = model.activities.toSet() - touchedActivities
            for (src in untouchedActivities)
                for (split in model.splits[src].orEmpty())
                    if (untouchedActivities.containsAll(split.targets))
                        splits.add(split)
            for (dst in untouchedActivities)
                for (join in model.joins[dst].orEmpty())
                    if (untouchedActivities.containsAll(join.sources))
                        joins.add(join)
        }
        logger.debug { "Preserving splits: $splits" }
        logger.debug { "Preserving joins: $joins" }

        model = MutableCausalNet(start = start, end = end)
        val activeDependencies = directlyFollows.filterValues { it >= 1 }.keys
        for (d in activeDependencies) {
            model.addInstance(d.source)
            model.addInstance(d.target)
            model.addDependency(d)
        }
        val toReplay = window.uniqueSet()
            .filter { trace -> trace.any { touchedActivities.contains(it) } }
            .map { listOf(start) + it + listOf(end) }
        val (newsplits, newjoins) = replayer.replayGroup(model, toReplay)
        splits.addAll(newsplits)
        joins.addAll(newjoins)
        for (split in splits) {
            if (!model.dependencies.containsAll(split.dependencies)) {
                println("This is a new split ${split in newsplits}")
                println("Missing: ${split.dependencies - model.dependencies}")
            }
            model.addSplit(split)
        }
        for (join in joins)
            model.addJoin(join)
    }

    /**
     * Add [log] to the current window and update the underlying model in [result]
     */
    override fun processLog(log: Log) =
        processDiff(log, Log(emptySequence()))

}
