package processm.etl.flink.artemis

import io.debezium.engine.DebeziumEngine
import io.debezium.engine.format.Json
import io.debezium.testing.testcontainers.ConnectorResolver
import org.apache.flink.api.common.eventtime.*
import org.apache.flink.api.common.typeinfo.TypeHint
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.cep.CEP
import org.apache.flink.cep.functions.PatternProcessFunction
import org.apache.flink.cep.nfa.aftermatch.AfterMatchSkipStrategy
import org.apache.flink.cep.pattern.Pattern
import org.apache.flink.cep.pattern.conditions.IterativeCondition
import org.apache.flink.formats.json.JsonNodeDeserializationSchema
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.util.Collector
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.core.esb.Artemis
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import java.sql.DriverManager
import java.time.Duration
import java.util.*
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FlinkPoC {

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
    fun postgres() {

        // Postgres Container setup

        val storageFileName = createTempFile()
        val password = UUID.randomUUID().toString()
        val imageName = DockerImageName.parse("debezium/postgres:11")
            .asCompatibleSubstituteFor("postgres")
        val postgresContainer = PostgreSQLContainer<PostgreSQLContainer<*>>(imageName)
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withEnv("POSTGRES_PASSWORD", password)
            .withPassword(password)
        Startables.deepStart(listOf(postgresContainer)).join()
        val connect = connect@{
            val connection =
                DriverManager.getConnection(
                    postgresContainer.jdbcUrl,
                    postgresContainer.username,
                    postgresContainer.password
                )
            connection.autoCommit = false
            return@connect connection
        }

        connect().use { connection ->
            connection.createStatement().use { s ->
                s.executeUpdate("create schema todo")
                s.executeUpdate("create table todo.orders (id int8 not null, status varchar(255), primary key (id))")
                s.executeUpdate("alter table todo.orders replica identity full")
                connection.commit()
            }
        }

        // Debezium setup

        val props = Properties()
        props.setProperty("name", "engine");
        props.setProperty("database.hostname", postgresContainer.containerIpAddress)
        val port = postgresContainer.getMappedPort(postgresContainer.exposedPorts[0])
        props.setProperty("database.port", port.toString())
        props.setProperty("database.user", postgresContainer.username)
        props.setProperty("database.password", postgresContainer.password)
        props.setProperty("database.dbname", postgresContainer.databaseName)
        props.setProperty("plugin.name", "pgoutput")
        val connector = ConnectorResolver.getConnectorByJdbcDriver(postgresContainer.driverClassName)
        props.setProperty("connector.class", connector)
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        props.setProperty("offset.storage.file.filename", storageFileName.absolutePath);
        props.setProperty("offset.flush.interval.ms", "1000") // 60000
        props.setProperty("database.server.name", "blah")

        val topic = UUID.randomUUID().toString()

        val engine = DebeziumEngine
            .create(Json::class.java)
            .using(props)
            .notifying(DebeziumToArtemis(topic))
            .build()

        val executor = Executors.newSingleThreadExecutor()
        executor.execute(engine)

        // Flink initialization

        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.parallelism = 1

        // Flink source

        val deserializer = JsonNodeDeserializationSchema()

        // This strategy is perhaps a bit naive, but I think it is sufficient as a PoC
        val strategy = object : WatermarkStrategy<ObjectNode> {

            override fun createTimestampAssigner(context: TimestampAssignerSupplier.Context?): TimestampAssigner<ObjectNode> =
                TimestampAssigner<ObjectNode> { element, _ -> element["payload"]["ts_ms"].asLong() }

            override fun createWatermarkGenerator(context: WatermarkGeneratorSupplier.Context?): WatermarkGenerator<ObjectNode> =
                object : WatermarkGenerator<ObjectNode> {

                    private var lastTimestamp: Long = 0
                    private val delay: Duration = Duration.ofSeconds(5)

                    override fun onEvent(event: ObjectNode?, eventTimestamp: Long, output: WatermarkOutput?) {
                        lastTimestamp = eventTimestamp
                    }

                    override fun onPeriodicEmit(output: WatermarkOutput?) {
                        val now = Calendar.getInstance().timeInMillis
                        if (lastTimestamp + delay.toMillis() < now)
                            output?.emitWatermark(Watermark(lastTimestamp))
                    }

                }

        }

        val artemisToFlink = FlinkArtemisSource<Pair<String, String>>(
            topic,
            TypeInformation.of(object : TypeHint<Pair<String, String>>() {})
        )

        val source = env
            .addSource(artemisToFlink)
            .map { input -> deserializer.deserialize(input.second.encodeToByteArray()) }
            .returns(object : TypeHint<ObjectNode>() {})
            .assignTimestampsAndWatermarks(strategy)

        // CEP - pattern definition

        val orderPattern = Pattern.begin<ObjectNode>("placed", AfterMatchSkipStrategy.skipToNext())
            .where(object : IterativeCondition<ObjectNode>() {
                override fun filter(value: ObjectNode, ctx: Context<ObjectNode>): Boolean {
                    return value["payload"]["before"].isNull && value["payload"]["after"]["status"].asText() == "placed"
                }
            })
            .followedBy("next")
            .where(object : IterativeCondition<ObjectNode>() {
                override fun filter(value: ObjectNode, ctx: Context<ObjectNode>): Boolean {
                    val orderId = ctx.getEventsForPattern("placed").first()["payload"]["after"]["id"].asInt()
                    return value["payload"].isObject && value["payload"]["before"].isContainerNode && !value["payload"]["before"]["id"].isNull && value["payload"]["before"]["id"].asInt() == orderId && value["payload"]["after"]["status"].asText() != "completed"
                }
            })
            .oneOrMore()
            .greedy()
            .followedBy("closed")
            .where(object : IterativeCondition<ObjectNode>() {
                override fun filter(value: ObjectNode, ctx: Context<ObjectNode>): Boolean {
                    val orderId = ctx.getEventsForPattern("placed").first()["payload"]["after"]["id"].asInt()
                    val after = value["payload"]["after"]
                    return after["id"].asInt() == orderId && after["status"].asText() == "completed"
                }
            })


        // CEP - pattern matching

        val patternStream = CEP.pattern(source, orderPattern)

        val logStream = patternStream.process(object : PatternProcessFunction<ObjectNode, ArrayList<String>>() {
            override fun processMatch(
                match: MutableMap<String, MutableList<ObjectNode>>,
                ctx: Context,
                out: Collector<ArrayList<String>>
            ) {
                val events = (match["placed"].orEmpty() + match["next"].orEmpty() + match["closed"].orEmpty())
                    .mapTo(ArrayList()) { it["payload"]["after"]["status"].asText() }
                out.collect(events)
            }
        })

        // CEP - collection and making a XES Log

        val traces = FlinkSequence<ArrayList<String>>()
        logStream.addSink(traces.sink)
        val log = Log(traces.map { events -> Trace(events.map { DBEvent(it) }.asSequence()) })

        // Executing Flink

        env.executeAsync()

        // here ends the PoC itself - the rest is testing

        val clients = listOf(
            listOf("placed", "delayed", "being collected", "delayed", "completed"),
            listOf("placed", "being collected", "shipped", "delivered", "completed")
        )

        val threadPool = ExecutorCompletionService<Unit>(Executors.newCachedThreadPool())
        clients.withIndex().map { (id, client) ->
            threadPool.submit {
                Thread.sleep(1000L * id)
                simulateOrderProcessing(id, client, connect)
            }
        }

        val actual =
            log.traces.take(clients.size).map { trace -> trace.events.map { it.conceptName }.toList() }.toList()

        assertEquals(clients.size, actual.size)
    }

    private fun simulateOrderProcessing(id: Int, statuses: List<String>, connect: () -> java.sql.Connection) {
        val rnd = Random(id)
        connect().use { connection ->
            connection.autoCommit = false
            val i = statuses.iterator()
            connection.prepareStatement("insert into todo.orders values (?, ?)").use { insert ->
                insert.setInt(1, id)
                insert.setString(2, i.next())
                insert.executeUpdate()
                connection.commit()
            }
            connection.prepareStatement("update todo.orders set status=? where id=?").use { update ->
                while (i.hasNext()) {
                    Thread.sleep(rnd.nextLong(600, 1400))
                    update.setString(1, i.next())
                    update.setInt(2, id)
                    update.executeUpdate()
                    connection.commit()
                }
            }
        }
    }
}
