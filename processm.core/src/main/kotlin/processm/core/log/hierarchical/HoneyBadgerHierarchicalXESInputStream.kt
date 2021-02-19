package processm.core.log.hierarchical

import processm.core.log.Event
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
            Log(traces.asSequence().map {(trace, events) ->
                Trace(events.asSequence())
            })
        }
    }

    override fun iterator(): Iterator<Log> = result.iterator()
}