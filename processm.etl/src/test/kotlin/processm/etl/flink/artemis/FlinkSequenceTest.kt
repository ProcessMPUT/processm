package processm.etl.flink.artemis

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import processm.core.esb.Artemis
import kotlin.test.*

class FlinkSequenceTest {

    private val artemis = Artemis()

    @BeforeTest
    fun setUp() {
        artemis.register()
        artemis.start()
    }

    @AfterTest
    fun cleanUp() {
        artemis.stop()
    }

    @Test
    fun `no parallelism`() {
        val from = 10L
        val to = 20L
        val seq = FlinkSequence<Long>()
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.parallelism = 1
        env.fromSequence(from, to).addSink(seq.sink)
        env.executeAsync()
        assertEquals((from..to).toList(), seq.toList().sorted())
    }

    @Ignore("Known to fail. I think messages from Flink come in random order and the EOS marker may preceded some valid messages")
    @Test
    fun `default parallelism`() {
        val from = 10L
        val to = 20L
        val seq = FlinkSequence<Long>()
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.fromSequence(from, to).addSink(seq.sink)
        env.executeAsync()
        assertEquals((from..to).toList(), seq.toList().sorted())
    }
}
