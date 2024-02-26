package processm.experimental.performance.perfectaligner

import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Binding
import processm.core.models.causalnet.CausalNet
import processm.experimental.helpers.BitIntSet
import processm.logging.debug
import processm.logging.logger
import processm.miners.causalnet.onlineminer.BasicTraceToNodeTrace
import processm.miners.causalnet.onlineminer.TraceToNodeTrace
import java.util.*


class PerfectAligner(
    val model: IntCausalNet,
    val converter: TraceToNodeTrace = BasicTraceToNodeTrace()
) {

    companion object {
        private val logger = logger()
    }


    private data class SearchState(
        val splitTime: Boolean,
        val position: Int,
        val executionState: ExecutionState,
        val binding: Int,
        val previousState: SearchState?
    )

    private class SearchStateComparator : Comparator<SearchState> {
        override fun compare(o1: SearchState?, o2: SearchState?): Int {  // <0 -> o1<o2; >0 -> o1>o2
            require(o1 != null)
            require(o2 != null)
            if (o2.position != o1.position)
                return o2.position - o1.position  // prefer further in the trace
            return o2.executionState.nTokens - o1.executionState.nTokens //prefer more tokens
        }
    }

    private lateinit var depToNextNode: IntArray
    private val maxTokens = ArrayList<IntArray>()

    constructor(base: CausalNet, converter: TraceToNodeTrace = BasicTraceToNodeTrace()) :
            this(IntCausalNet(base), converter)

    fun traceToInt(trace: Trace): List<Int> =
        converter(trace).map { model.nodeEncoder[it] ?: error("Unknown node $it") }

    private fun decodeAlignment(searchState: SearchState): List<Binding> {
        val result = ArrayList<Binding>()
        var current = searchState
        while (current.binding >= 0) {
            val binding = if (current.splitTime)
                model.joinsDecoder[current.binding] // if it is split time it means that the last choice was join
            else
                model.splitsDecoder[current.binding]
            result.add(binding)
            current = current.previousState ?: break
        }
        result.reverse()
        return result
    }

    fun perfectFitRatio(log: Log, maxVisitedCoefficient: Int = 0): Double {
        var nAligned = 0
        for (trace in log.traces) {
            val maxVisited =
                if (maxVisitedCoefficient <= 0) Int.MAX_VALUE else maxVisitedCoefficient * trace.events.count()
            if (align(trace, maxVisited) != null)
                nAligned++
        }
        return nAligned.toDouble() / log.traces.count()
    }

    fun align(trace: Trace, maxVisited: Int = Int.MAX_VALUE): List<Binding>? {
        val intTrace = try {
            traceToInt(trace).toMutableList()
        } catch (e: IllegalStateException) {
            return null
        }
        if (intTrace[0] != model.start)
            intTrace.add(0, model.start)
        if (intTrace.last() != model.end)
            intTrace.add(model.end)
        return align(intTrace, maxVisited)
    }

    fun align(trace: List<Int>, maxVisited: Int = Int.MAX_VALUE): List<Binding>? {
        maxTokens.clear()
        for (pos in trace.indices) {
            val tmp = IntArray(model.nNodes)
            for (i in pos until trace.size)
                tmp[trace[i]] += 1
            maxTokens.add(tmp)
        }

        depToNextNode = IntArray(trace.size - 1)
        for (pos in 0 until trace.size - 1) {
            val currentNode = trace[pos]
            val nextNode = trace[pos + 1]
            depToNextNode[pos] = -1
            for (dep in 0 until model.nDeps)
                if (model.dep2Source[dep] == currentNode && model.dep2Target[dep] == nextNode) {
                    depToNextNode[pos] = dep
                    break
                }
        }

        var ctr = 0

        val queue = PriorityQueue<SearchState>(SearchStateComparator())
        queue.add(SearchState(true, 0, ExecutionState(model), -1, null))

        while (queue.isNotEmpty()) {
            val searchState = queue.poll()
            ctr++
            if (ctr > maxVisited)
                break
            if (searchState.splitTime) {
                handleSplit(trace, searchState, queue)
            } else {

                val alignment = handleJoin(searchState, trace, queue)
                if (alignment != null) {
                    logger.debug { "ctr=$ctr: $alignment" }
                    return alignment
                }
            }
        }
        logger.debug { "ctr=$ctr no alignment" }
        return null
    }

    private fun handleJoin(
        searchState: SearchState,
        trace: List<Int>,
        queue: PriorityQueue<SearchState>
    ): List<Binding>? {
        if (searchState.position >= trace.size)
            return null
        val currentNode = trace[searchState.position]
        val incomingDeps = model.incomingDeps[currentNode]
        val maxTokens = if (searchState.position < trace.size - 1)
            maxTokens[searchState.position + 1][currentNode]
        else
            -1
        for (join in searchState.executionState.activeJoins) {
            if (model.join2Target[join] == currentNode) {
                if (maxTokens >= 0 && !canConsumeAllTokens(maxTokens, incomingDeps, searchState.executionState, join))
                    continue
                val newState = searchState.executionState.copy()
                newState.executeJoin(join)
                if (searchState.position < trace.size - 1) {
                    val newSearchState = SearchState(true, searchState.position, newState, join, searchState)
                    queue.add(newSearchState)
                } else {
                    if (newState.nTokens == 0) {
                        val newSearchState = SearchState(true, searchState.position, newState, join, searchState)
                        return decodeAlignment(newSearchState)
                    }
                }
            }
        }
        return null
    }


    private fun canConsumeAllTokens(
        maxTokens: Int,
        deps: BitIntSet,
        executionState: ExecutionState,
        joinIdx: Int
    ): Boolean {
        val join = model.flatJoins[joinIdx]
        for (dep in deps) {
            val token = if (dep in join) 1 else 0
            if (executionState.tokens[dep] - token > maxTokens)
                return false
        }
        return true
    }

    private fun canAddTokens(split: Int, executionState: ExecutionState, maxTokens: IntArray): Boolean {
        for (dep in model.flatSplits[split])
            if (executionState.tokens[dep] >= maxTokens[model.dep2Target[dep]])
                return false
        return true
    }

    private fun handleSplit(
        trace: List<Int>,
        searchState: SearchState,
        queue: PriorityQueue<SearchState>
    ) {
        val currentNode = trace[searchState.position]
        val nextNode = trace[searchState.position + 1]
        val depToNextNode = this.depToNextNode[searchState.position]
        val nextAlreadyActive = nextNode in searchState.executionState.activeNodes
        if (!nextAlreadyActive && depToNextNode < 0) // this situation cannot be recovered - next node cannot be joined and there's no dependency that would enable it
            return
        for (split in model.splits[currentNode]) {
            if (!nextAlreadyActive && depToNextNode !in model.flatSplits[split])
                continue
            val maxTokens = maxTokens[searchState.position + 1]
            if (!canAddTokens(split, searchState.executionState, maxTokens))
                continue
            val newState = searchState.executionState.copy()
            newState.executeSplit(split)
            val newSearchState = SearchState(false, searchState.position + 1, newState, split, searchState)
            queue.add(newSearchState)
        }
    }
}
