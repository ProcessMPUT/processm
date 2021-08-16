package processm.experimental.etl.flink.artemis

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeparateSink(val file:File): SinkFunction<Long> {

    override fun invoke(value: Long, context: SinkFunction.Context?) {
        file.appendText("$value${System.lineSeparator()}")
    }
}

class FlinkSerializationIssuesTest {

    // Throws InvalidProgramException inside the thread
    @Test
    fun `thread inline`() {
        val from = 10L
        val to = 20L
        val expected = (from..to).toList()

        val file = Files.createTempFile(null, null).toFile()
        file.deleteOnExit()

        Thread {
            val env = StreamExecutionEnvironment.getExecutionEnvironment()
            env.parallelism = 1

            val source = env.fromCollection((10L..20L).toList())

            val sink = object : SinkFunction<Long> {

                override fun invoke(value: Long, context: SinkFunction.Context?) {
                    file.appendText("$value${System.lineSeparator()}")
                }
            }

            source.print()
            source.addSink(sink)
            env.executeAsync()
        }.start()


        Thread.sleep(5000)

        val actual = file.readLines().map { Integer.parseInt(it).toLong() }
        assertTrue { actual.size >= expected.size }
        assertEquals(expected, actual.sorted().subList(0, expected.size))
    }

    @Test
    fun `thread inline with separate sink`() {
        val from = 10L
        val to = 20L
        val expected = (from..to).toList()

        val file = Files.createTempFile(null, null).toFile()
        file.deleteOnExit()

        Thread {
            val env = StreamExecutionEnvironment.getExecutionEnvironment()
            env.parallelism = 1

            val source = env.fromCollection((10L..20L).toList())

            val sink = SeparateSink(file)

            source.print()
            source.addSink(sink)
            env.executeAsync()
        }.start()


        Thread.sleep(5000)

        val actual = file.readLines().map { Integer.parseInt(it).toLong() }
        assertTrue { actual.size >= expected.size }
        assertEquals(expected, actual.sorted().subList(0, expected.size))
    }

    @Test
    fun `thread inline 2`() {
        val from = 10L
        val to = 20L
        val expected = (from..to).toList()

        val file = Files.createTempFile(null, null).toFile()
        file.deleteOnExit()

        object: Thread() {
            override fun run() {
                val env = StreamExecutionEnvironment.getExecutionEnvironment()
                env.parallelism = 1

                val source = env.fromCollection((10L..20L).toList())

                val sink = object : SinkFunction<Long> {

                    override fun invoke(value: Long, context: SinkFunction.Context?) {
                        file.appendText("$value${System.lineSeparator()}")
                    }
                }

                source.print()
                source.addSink(sink)
                env.executeAsync()
            }
        }.start()


        Thread.sleep(5000)

        val actual = file.readLines().map { Integer.parseInt(it).toLong() }
        assertTrue { actual.size >= expected.size }
        assertEquals(expected, actual.sorted().subList(0, expected.size))
    }

    @Test
    fun `no thread`() {
        val from = 10L
        val to = 20L
        val expected = (from..to).toList()

        val file = Files.createTempFile(null, null).toFile()
        file.deleteOnExit()

//        Thread {
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.parallelism = 1

        val source = env.fromCollection((10L..20L).toList())

        val sink = object : SinkFunction<Long> {

            override fun invoke(value: Long, context: SinkFunction.Context?) {
                file.appendText("$value${System.lineSeparator()}")
            }
        }

        source.print()
        source.addSink(sink)
        env.executeAsync()
//        }.start()


        Thread.sleep(5000)

        val actual = file.readLines().map { Integer.parseInt(it).toLong() }
        assertTrue { actual.size >= expected.size }
        assertEquals(expected, actual.sorted().subList(0, expected.size))
    }

    @Test
    fun `thread with lambda`() {
        val from = 10L
        val to = 20L
        val expected = (from..to).toList()

        val file = Files.createTempFile(null, null).toFile()
        file.deleteOnExit()

        val helper = {
            val env = StreamExecutionEnvironment.getExecutionEnvironment()
            env.parallelism = 1

            val source = env.fromCollection((10L..20L).toList())

            val sink = object : SinkFunction<Long> {

                override fun invoke(value: Long, context: SinkFunction.Context?) {
                    file.appendText("$value${System.lineSeparator()}")
                }
            }

            source.print()
            source.addSink(sink)
            env.executeAsync()
        }

        Thread {
            helper()
        }.start()


        Thread.sleep(5000)

        val actual = file.readLines().map { Integer.parseInt(it).toLong() }
        assertTrue { actual.size >= expected.size }
        assertEquals(expected, actual.sorted().subList(0, expected.size))
    }

    private fun separateFunction(file: File) {
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.parallelism = 1

        val source = env.fromCollection((10L..20L).toList())

        val sink = object : SinkFunction<Long> {

            override fun invoke(value: Long, context: SinkFunction.Context?) {
                file.appendText("$value${System.lineSeparator()}")
            }
        }

        source.print()
        source.addSink(sink)
        env.executeAsync()
    }

    @Test
    fun `thread with separate function`() {
        val from = 10L
        val to = 20L
        val expected = (from..to).toList()

        val file = Files.createTempFile(null, null).toFile()
        file.deleteOnExit()

        Thread {
            separateFunction(file)
        }.start()


        Thread.sleep(5000)

        val actual = file.readLines().map { Integer.parseInt(it).toLong() }
        assertTrue { actual.size >= expected.size }
        assertEquals(expected, actual.sorted().subList(0, expected.size))
    }
}
