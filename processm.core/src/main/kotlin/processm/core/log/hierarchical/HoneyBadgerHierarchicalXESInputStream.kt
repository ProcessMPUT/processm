package processm.core.log.hierarchical

import processm.core.log.Event
import processm.core.log.XESComponent
import processm.core.log.XESInputStream
import processm.core.log.Log as BaseLog
import processm.core.log.Trace as BaseTrace

@RequiresOptIn("In-memory XES processing is suitable only for testing and development and should not be used in production. Use @InMemoryXESProcessing to remove this error.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class InMemoryXESProcessing

/**
 * Honey badger doesn't care about memory consumption and keeps everything in memory.
 *
 * Suitable for testing and development. Unsuitable for production. Possibly buggy. Use at your own risk.
 */
@InMemoryXESProcessing
class HoneyBadgerHierarchicalXESInputStream(base: XESInputStream) : LogInputStream {
    private lateinit var logStarts: IntArray
    private lateinit var traceStarts: IntArray
    private val materialized: List<XESComponent> by lazy {
        val materialized = base.toList()
        val logStarts = ArrayList<Int>()
        val traceStarts = ArrayList<Int>()
        for ((i, c) in materialized.withIndex()) {
            when (c) {
                is BaseTrace -> traceStarts.add(i)
                is BaseLog -> logStarts.add(i)
            }
        }

        this.logStarts = logStarts.toIntArray()
        this.traceStarts = traceStarts.toIntArray()

        return@lazy materialized
    }


    override fun iterator(): Iterator<Log> = sequence {
        if (materialized.isEmpty()) // this line initializes fields due to the lazy initializer above
            return@sequence

        for ((i, lStartInc) in logStarts.withIndex()) {
            val lEndExc = if (i < logStarts.size - 1) logStarts[i + 1] else materialized.size
            val log = Log(
                traces = traceStarts
                    .asSequence()
                    .withIndex()
                    .filter { (_, tStart) -> lStartInc < tStart && tStart < lEndExc }
                    .map { (j, tStartInc) ->
                        val tEndExc =
                            if (j < traceStarts.size - 1 && traceStarts[j + 1] < lEndExc) traceStarts[j + 1] else lEndExc
                        Trace(
                            events = materialized.subList(tStartInc + 1, tEndExc).asSequence() as Sequence<Event>,
                            attributesInternal = materialized[tStartInc].attributesInternal
                        )
                    },
                attributesInternal = materialized[lStartInc].attributesInternal
            )
            yield(log)
        }
    }.iterator()
}

/**
 * Honey badger doesn't care about memory consumption and keeps everything in memory.
 *
 * Suitable for testing and development. Unsuitable for production. Possibly buggy. Use at your own risk.
 */
@InMemoryXESProcessing
@Deprecated(
    "Use HoneyBadgerHierarchicalXESInputStream instead",
    replaceWith = ReplaceWith("HoneyBadgerHierarchicalXESInputStream")
)
class HoneyBadgerHierarchicalXESInputStreamOld(base: XESInputStream) : LogInputStream {

    private val result: List<Log> by lazy {
        val data = ArrayList<Pair<BaseLog, ArrayList<Pair<BaseTrace, ArrayList<Event>>>>>()
        for (element in base) {
            when (element) {
                is BaseLog -> data.add(element to ArrayList())

                is BaseTrace -> data.last().second.add(element to ArrayList())
                is Event -> data.last().second.last().second.add(element)
                else -> throw IllegalStateException()

            }
        }
        return@lazy data.map { (log, traces) ->
            Log(traces.asSequence().map { (trace, events) ->
                Trace(events.asSequence(), trace.attributesInternal)
            }, log.attributesInternal)
        }
    }

    override fun iterator(): Iterator<Log> = result.iterator()
}
