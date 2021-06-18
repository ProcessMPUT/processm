package processm.etl

import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import processm.core.esb.Service
import processm.core.esb.ServiceStatus
import java.io.Serializable
import java.util.*
import javax.jms.*
import javax.naming.InitialContext

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

    private data class FlinkSequenceMessage<T>(val data: T?) : Serializable

    private class Producer<T>(val topic: String) : RichSinkFunction<T?>(), Service {

        override var status: ServiceStatus = ServiceStatus.Unknown
            private set
        override val name: String = "$topic: FlinkSequenceProducer"


        private lateinit var jmsConnection: TopicConnection
        private lateinit var jmsSession: TopicSession
        private lateinit var jmsPublisher: TopicPublisher

        override fun register() {
            status = ServiceStatus.Stopped
        }

        override fun start() {
            val jmsContext = InitialContext()
            val jmsConnFactory = jmsContext.lookup("ConnectionFactory") as TopicConnectionFactory
            jmsConnection = jmsConnFactory.createTopicConnection()
            jmsSession = jmsConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
            jmsPublisher = jmsSession.createPublisher(jmsSession.createTopic(topic))
            jmsConnection.start()
            status = ServiceStatus.Started
        }

        override fun stop() {
            send(null)
            jmsPublisher.close()
            jmsSession.close()
            jmsConnection.close()
            status = ServiceStatus.Stopped
        }

        private fun send(e: T?) =
            jmsPublisher.publish(jmsSession.createObjectMessage(FlinkSequenceMessage(e)))


        override fun invoke(value: T?, context: SinkFunction.Context?) {
            if (value != null)
                send(value)
        }

        override fun open(parameters: Configuration?) = start()

        override fun close() = stop()
    }

    private class Consumer<T>(val topic: String) : Iterator<T>, Service {

        private lateinit var jmsConnection: TopicConnection
        private lateinit var jmsSession: TopicSession
        private lateinit var jmsConsumer: MessageConsumer
        private var streamClosed: Boolean = false

        private var nextMessageBackend: FlinkSequenceMessage<T>? = null

        private fun nextMessage(): FlinkSequenceMessage<T> {
            if (!this::jmsConnection.isInitialized)
                start()
            if (nextMessageBackend == null)
                nextMessageBackend = (jmsConsumer.receive() as ObjectMessage).`object` as FlinkSequenceMessage<T>
            return nextMessageBackend!!
        }

        override fun hasNext(): Boolean {
            if (streamClosed)
                return false
            if (nextMessage().data != null)
                return true
            stop()
            return false
        }

        override fun next(): T = nextMessage().data!!.also { nextMessageBackend = null }

        override fun register() {
            status = ServiceStatus.Stopped
        }

        override var status: ServiceStatus = ServiceStatus.Unknown
        override val name: String = "$topic: FlinkSequenceConsumer"

        override fun start() {
            check(!streamClosed)
            val jmsContext = InitialContext()
            val jmsConnFactory = jmsContext.lookup("ConnectionFactory") as TopicConnectionFactory
            jmsConnection = jmsConnFactory.createTopicConnection()
            jmsSession = jmsConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
            jmsConsumer = jmsSession.createSharedDurableConsumer(jmsSession.createTopic(topic), name)
            jmsConnection.start()
            status = ServiceStatus.Started
        }

        override fun stop() {
            jmsConsumer.close()
            jmsSession.close()
            jmsConnection.close()
            status = ServiceStatus.Stopped
            streamClosed = true
        }

    }

    private val topic: String = UUID.randomUUID().toString()

    private val iterator: Consumer<T> = Consumer(topic)

    /**
     * Apache Flink sink producing elements for this sequence
     */
    val sink: RichSinkFunction<T?> = Producer(topic)

    override fun iterator(): Iterator<T> = iterator

}