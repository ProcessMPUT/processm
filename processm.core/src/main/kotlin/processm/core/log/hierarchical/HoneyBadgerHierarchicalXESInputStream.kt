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
    /**
     * Upper Int holds the index in [traceStarts] of the first trace.
     * Lower Int holds the index in [materialized] of the log.
     */
    private lateinit var logStarts: LongArray
    private lateinit var traceStarts: IntArray
    private val materialized: List<XESComponent> by lazy {
        val materialized = base.toList()
        val logStarts = ArrayList<Long>()
        val traceStarts = ArrayList<Int>()
        for ((i, c) in materialized.withIndex()) {
            when (c) {
                is BaseTrace -> traceStarts.add(i)
                is BaseLog -> logStarts.add((traceStarts.size.toLong() shl Int.SIZE_BITS) or i.toLong())
            }
        }

        this.logStarts = logStarts.toLongArray()
        this.traceStarts = traceStarts.toIntArray()

        return@lazy materialized
    }


    override fun iterator(): Iterator<Log> = sequence {
        if (materialized.isEmpty()) // this line initializes fields due to the lazy initializer above
            return@sequence

        for ((i, lStartRaw) in logStarts.withIndex()) {
            val lStartInc = lStartRaw.toInt()
            val tStartIdx = (lStartRaw shr Int.SIZE_BITS).toInt()
            val lEndRaw =
                if (i < logStarts.size - 1) logStarts[i + 1]
                else (traceStarts.size.toLong() shl Int.SIZE_BITS) or materialized.size.toLong()
            val tEndIdx = (lEndRaw shr Int.SIZE_BITS).toInt()
            val lEndExc = lEndRaw.toInt()
            val log = Log(
                traces = traceStarts.asList() // this is just view rather than a copy
                    .subList(tStartIdx, tEndIdx) // this is view too
                    .asSequence()
                    .withIndex()
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
