package processm.etl.flink

import io.debezium.engine.DebeziumEngine
import io.debezium.engine.format.Json
import io.debezium.testing.testcontainers.ConnectorResolver
import org.apache.flink.api.common.typeinfo.TypeHint
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.core.esb.Artemis
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.logger
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FlinkPoC2 {

    companion object {
        private val logger = logger()
    }

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
    fun test() {

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
                s.executeUpdate("create table todo.orders (id int8 not null, timestamp TIMESTAMP DEFAULT localtimestamp, status varchar(255))")
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

        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.parallelism = 1


        // Connecting Flink to Debezium - creating a virtual table constructed from Debezium JSON's received via Artemis
        val tableEnv = StreamTableEnvironment.create(env)

        // In theory Flink 1.13 should be able to handle TIMESTAMP WITH TIME ZONE as returned by current_timestamp of PostgreSQL. In practice - it fails on parsing the CREATE TABLE statement.
        tableEnv.executeSql(
            """
            CREATE TABLE topic_products (
  `id` BIGINT, `timestamp` BIGINT, 
  `status` string, 
  `parsed_timestamp` AS TO_TIMESTAMP_LTZ(`timestamp`/1000, 3),
  WATERMARK FOR `parsed_timestamp` AS `parsed_timestamp`
) WITH (
 'connector' = 'artemis',
 'topic' = '$topic',
 'format' = 'debezium-json',
 'debezium-json.schema-include' = 'true'
)
        """.trimIndent()
        )

        // CEP using MATCH_RECOGNIZE
        val first = "first"
        val intermediate = "intermediate"
        val last = "last"
        val separator = ";"

        val result = tableEnv.sqlQuery(
            """
          SELECT *
          FROM topic_products
            MATCH_RECOGNIZE (
              PARTITION BY `id`
              ORDER BY  `parsed_timestamp`
              MEASURES
                A.status AS $first,
                LISTAGG(B.status, '$separator') AS $intermediate,
                C.status AS $last
              ONE ROW PER MATCH
              AFTER MATCH SKIP PAST LAST ROW
              PATTERN (A B* C) WITHIN INTERVAL '1' HOUR
              DEFINE
                A AS A.status = 'placed',
                B AS B.status not in ('placed', 'completed'),
                C AS C.status = 'completed'
            )
         """
        )

        // Extracting events recognized by CEP and transforming them into XES traces
        val traces = FlinkSequence<ArrayList<String>>()

        tableEnv
            .toDataStream(result)
            .map {
                val result = ArrayList<String>()
                result.add(it.getField(first).toString())
                result.addAll(it.getField(intermediate).toString().split(separator))
                result.add(it.getField(last).toString())
                return@map result
            }
            .returns(object : TypeHint<ArrayList<String>>() {})
            .addSink(traces.sink)
        val log = Log(traces.map { events ->
            logger.info("Received trace $events")
            Trace(events.map { DBEvent(it) }.asSequence())
        })

        // Start Flink

        env.executeAsync()

        // Simulate clients

        val clients = listOf(
            listOf("placed", "delayed", "being collected", "delayed", "completed"),
            listOf("placed", "being collected", "shipped", "delivered", "completed")
        )

        val threadPool = ExecutorCompletionService<Unit>(Executors.newCachedThreadPool())

        clients.withIndex().map { (id, client) ->
            threadPool.submit {
                Thread.sleep(1000L * id)
                logger.info("Issuing transaction $client")
                simulateOrderProcessing(id, client, connect)
            }
        }

        // Actual testing

        val actual =
            log.traces.take(clients.size).map { trace -> trace.events.map { it.conceptName }.toList() }.toList()

        assertEquals(clients.size, actual.size)
        for ((exp, act) in clients zip actual) {
            assertEquals(exp.size, act.size)
            for ((e, a) in exp zip act)
                assertEquals(e, a)
        }
    }

    private fun simulateOrderProcessing(id: Int, statuses: List<String>, connect: () -> java.sql.Connection) {
        try {
            val rnd = Random(id)
            connect().use { connection ->
                connection.autoCommit = false
                for (s in statuses) {
                    Thread.sleep(rnd.nextLong(600, 1400))
                    connection.prepareStatement("insert into todo.orders (id, status) values (?, ?)").use { insert ->
                        insert.setInt(1, id)
                        insert.setString(2, s)
                        insert.executeUpdate()
                        connection.commit()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
