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
 * Honey badger doesn't give a ... about memory consumption and keeps everything in memory.
 *
 * Suitable for testing and development. Unsuitable for production. Possibly buggy. Use at your own risk.
 */
@InMemoryXESProcessing
class HoneyBadgerHierarchicalXESInputStream(base: XESInputStream) : LogInputStream {

    private val log2traces = HashMap<BaseLog, MutableList<BaseTrace>>()
    private val trace2event = HashMap<BaseTrace, MutableList<Event>>()
    private val result: Sequence<Log> by lazy {
        var currentBaseLog: BaseLog? = null
        var currentBaseTrace: BaseTrace? = null
        for (element in base) {
            when (element) {
                is BaseLog -> currentBaseLog = element

                is BaseTrace -> {
                    currentBaseTrace = element
                    log2traces
                        .getOrPut(currentBaseLog ?: throw IllegalStateException(), { ArrayList() })
                        .add(element)
                }
                is Event -> {
                    trace2event
                        .getOrPut(currentBaseTrace ?: throw IllegalStateException(), { ArrayList() })
                        .add(element)
                }
                else -> throw IllegalStateException()

            }
        }
        log2traces.asSequence().map { (log, traces) ->
            Log(traces.asSequence()
                .map { trace -> Trace(trace2event.getOrDefault(trace, mutableListOf()).asSequence()) })
        }
    }

    override fun iterator(): Iterator<Log> = result.iterator()
}