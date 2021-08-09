package processm.etl.flink

import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import java.io.Serializable
import java.util.*

/**
 * Exposes a Flink DataStream as a [Sequence] using JMS as an intermediary.
 *
 * Usage example:
 * ```
 * val seq = FlinkSequence<Long>()
 * val source : DataStream = ...
 * source.addSink(seq.sink)
 * ```
 */
class FlinkSequence<T : Serializable> : Sequence<T> {

    private class Producer<T : Serializable>(val topic: String) : RichSinkFunction<T?>() {

        @Transient
        private var producerBackend: TopicProducer<T>? = null

        private fun producer(): TopicProducer<T> {
            val logger = logger()
            try {
                logger.enter()
                if (producerBackend == null) {
                    producerBackend = TopicProducer(topic)
                    producerBackend!!.register()
                }
                return producerBackend!!
            } finally {
                logger.exit()
            }
        }

        override fun invoke(value: T?, context: SinkFunction.Context?) {
            val logger = logger()
            try {
                logger.enter()
                if (value != null)
                    producer().send(value)
            } finally {
                logger.exit()
            }
        }

        override fun open(parameters: Configuration?) = producer().start()

        override fun close() = producer().stop()
    }

    private val topic: String = UUID.randomUUID().toString()

    private val iterator = TopicConsumer<T>(topic)

    init {
        iterator.register()
        iterator.start()
    }

    /**
     * Apache Flink sink producing elements for this sequence
     */
    val sink: RichSinkFunction<T?> = Producer(topic)

    override fun iterator(): Iterator<T> = iterator

}
