package processm.etl.flink.artemis

import io.debezium.engine.ChangeEvent
import java.util.function.Consumer

class DebeziumToArtemis(val topic: String) : Consumer<ChangeEvent<String, String>> {

    private val topicProducer = TopicProducer<Pair<String, String>>(topic)

    init {
        topicProducer.register()
        topicProducer.start()
    }

    override fun accept(record: ChangeEvent<String, String>) {
        topicProducer.send(record.key() to record.value())
    }
}
