package processm.etl.tracker

import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jgrapht.graph.DefaultDirectedGraph
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.core.helpers.mapToSet
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.persistence.Migrator
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.DatabaseChecker
import processm.core.querylanguage.Query
import processm.dbmodels.models.Relationship
import processm.dbmodels.models.Relationships
import processm.etl.discovery.SchemaCrawlerExplorer
import processm.etl.metamodel.*
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.sql.DriverManager
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DebeziumChangeTrackerTest {

    private val queries1 = listOf(
        """insert into EBAN VALUES(1, 'be1'); 
                        insert into EKET VALUES(1, 1, 'ae1');""",
        """ 
                        update EBAN set text='be2' where id=1;
                        update EKET set text='ae2' where id=1;
                        insert into EKKO VALUES(1, 1, 'ce1'); 
                        insert into EKPO VALUES(1, 1, 'de1');
                        insert into EKPO VALUES(2, 1, 'de2');
                    """,
        """
                       insert into EKKO VALUES(2, 1, 'ce2');
                       insert into EKPO VALUES(3, 2, 'de3');
                    """,
        """
                       insert into EBAN VALUES(2, 'be3'); 
                       insert into EKET VALUES(2, 2, 'ae3');
                       insert into EKKO VALUES(3, 2, 'ce3');
                       insert into EKPO VALUES(4, 3, 'de4');
                    """
    )

    private val queries2 = queries1.flatMap { it.split("\n") }.map { it.trim() }.filter { it.isNotEmpty() }

    private val queries3 = listOf(
        """insert into EBAN VALUES(1, 'be1'); 
                        insert into EKET VALUES(1, 1, 'ae1');""",
        """                         
                        insert into EKKO VALUES(1, 1, 'ce1'); 
                        insert into EKPO VALUES(1, 1, 'de1');
                        insert into EKPO VALUES(2, 1, 'de2');
                        update EBAN set text='be2' where id=1;
                        update EKET set text='ae2' where id=1;
                    """,
        """
                       insert into EKKO VALUES(2, 1, 'ce2');
                       insert into EKPO VALUES(3, 2, 'de3');
                    """,
        """
                       insert into EBAN VALUES(2, 'be3'); 
                       insert into EKET VALUES(2, 2, 'ae3');
                       insert into EKKO VALUES(3, 2, 'ce3');
                       insert into EKPO VALUES(4, 3, 'de4');
                    """
    )

    private val queries4 = listOf(
        """insert into EBAN VALUES(1, 'be1'); 
                        insert into EKET VALUES(1, 1, 'ae1');""",
        """                         
                        insert into EKKO VALUES(1, 1, 'ce1'); 
                        insert into EKPO VALUES(1, 1, 'de1');
                        insert into EKPO VALUES(2, 1, 'de2');                        
                    """,
        """                       
                       insert into EKKO VALUES(2, 1, 'ce2');
                       insert into EKPO VALUES(3, 2, 'de3');
                       insert into EBAN VALUES(2, 'be3');
                    """,
        """                                           
                       insert into EKET VALUES(2, 2, 'ae3');
                       insert into EKKO VALUES(3, 2, 'ce3');
                       insert into EKPO VALUES(4, 3, 'de4');
                    """,
        """update EBAN set text='be2' where id=1; 
                       update EKET set text='ae2' where id=1;"""
    )

    private lateinit var container: PostgreSQLContainer<*>
    private lateinit var containerJdbcURL: String
    private lateinit var dataStoreId: UUID
    private lateinit var targetDBName: String
    private lateinit var dataStoreDBName: String
    private lateinit var tempDir: File

    @BeforeAll
    fun setupDB() {
        val image =
            DockerImageName.parse("debezium/postgres:12").asCompatibleSubstituteFor("postgres")
        val user = "postgres"
        val password = "postgres"
        container = PostgreSQLContainer(image)
            .withDatabaseName("processm")
            .withUsername(user)
            .withPassword(password)
            .withReuse(false)
        Startables.deepStart(listOf(container)).join()
    }

    @AfterAll
    fun takeDownDB() {
        container.close()
    }

    @BeforeTest
    fun setup() {
        targetDBName = UUID.randomUUID().toString()
        dataStoreId = UUID.randomUUID()
        dataStoreDBName = dataStoreId.toString()
        tempDir = Files.createTempDirectory("processm").toFile()
        container.createConnection("").use { connection ->
            connection.prepareStatement("CREATE DATABASE \"$targetDBName\"").use {
                it.execute()
            }
        }
        containerJdbcURL = container.jdbcUrl.replace(
            Regex("/[^/]*$"),
            "/$targetDBName?user=${container.username}&password=${container.password}"
        )
        Migrator.migrate(dataStoreDBName)
    }

    @AfterTest
    fun cleanup() {
        container.createConnection("").use { connection ->
            connection.prepareStatement("DROP DATABASE \"$targetDBName\"").use {
                it.execute()
            }
        }
        DriverManager.getConnection(DatabaseChecker.baseConnectionURL).use { connection ->
            DBCache.get(dataStoreDBName).close()
            connection.prepareStatement("DROP DATABASE \"$dataStoreDBName\"").use {
                it.execute()
            }
        }
        tempDir.deleteRecursively()
    }

    private val debeziumConfiguration: Properties
        get() = Properties().apply {
            val databaseURL =
                URI(containerJdbcURL.substring(5))   //skip jdbc: as URI seems to break on a protocol with multiple colons
            setProperty("name", "engine")
            setProperty("plugin.name", "pgoutput")
            setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
            setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
            setProperty("offset.storage.file.filename", File(tempDir, "storage").absolutePath)
            setProperty("offset.flush.interval.ms", "60000")
            setProperty("database.history", "io.debezium.relational.history.FileDatabaseHistory")
            setProperty("database.history.file.filename", File(tempDir, "dbhistory.dat").absolutePath)
            setProperty("database.server.id", "")
            setProperty("database.server.name", "my-app-connector")
            setProperty("database.hostname", databaseURL.host)
            setProperty("database.port", databaseURL.port.toString())
            setProperty("database.user", container.username)
            setProperty("database.password", container.password)
            setProperty("database.dbname", targetDBName)
            // properties for the testing environment; in particular the doc says that "slot.drop.on.stop" should be set on true only in test/development envs
            setProperty("slot.drop.on.stop", "true")
            setProperty("slot.name", UUID.randomUUID().toString().replace("-", "").lowercase())
        }


    @Test
    fun `v1 table 2 id a`() {
        testBase(
            setOf("eban", "eket"), listOf(
                setOf(setOf("ae1", "be1")),
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2")),
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3")),
                setOf(
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3"),
                    setOf("ae3", "be3", "ce3", "de4")
                ),
            ), queries1
        )
    }

    @Test
    fun `v1 table 2 id b`() {
        testBase(
            setOf("eban", "eket", "ekko"), listOf(
                setOf(setOf("ae1", "be1")),
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2")),
                setOf(
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2"),
                    setOf("ae1", "ae2", "be1", "be2", "ce2", "de3")
                ),
                setOf(
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2"),
                    setOf("ae1", "ae2", "be1", "be2", "ce2", "de3"),
                    setOf("ae3", "be3", "ce3", "de4")
                ),
            ), queries1
        )
    }

    @Test
    fun `v1 table 2 id c`() {
        testBase(
            setOf("eban", "eket", "ekko", "ekpo"), listOf(
                setOf(setOf("ae1", "be1")),
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"), setOf("ae1", "ae2", "be1", "be2", "ce1", "de2")),
                setOf(
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de2"),
                    setOf("ae1", "ae2", "be1", "be2", "ce2", "de3")
                ),
                setOf(
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de2"),
                    setOf("ae1", "ae2", "be1", "be2", "ce2", "de3"),
                    setOf("ae3", "be3", "ce3", "de4")
                ),
            ), queries1
        )
    }

    @Test
    fun `v2 table 2 id a`() {
        testBase(
            setOf("eban", "eket"), listOf(
                setOf(setOf("be1")), //be1
                setOf(setOf("be1", "ae1")), //ae1
                setOf(setOf("be1", "ae1", "be2")), //be2
                setOf(setOf("be1", "ae1", "be2", "ae2")), //ae2
                setOf(setOf("be1", "ae1", "be2", "ae2", "ce1")), //ce1
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1")), //de1
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2")), //de2
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2", "ce2")), //ce2
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3")), //de3
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3"), setOf("be3")), //be3
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3"), setOf("ae3", "be3")), //ae3
                setOf(
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3"),
                    setOf("ae3", "be3", "ce3")
                ), //ce3
                setOf(
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3"),
                    setOf("ae3", "be3", "ce3", "de4")
                ), //de4
            ), queries2
        )
    }

    @Test
    fun `v3 table 2 id c`() {
        testBase(
            setOf("eban", "eket", "ekko", "ekpo"), listOf(
                setOf(setOf("ae1", "be1")),
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"), setOf("ae1", "ae2", "be1", "be2", "ce1", "de2")),
                setOf(
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de2"),
                    setOf("ae1", "ae2", "be1", "be2", "ce2", "de3")
                ),
                setOf(
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de2"),
                    setOf("ae1", "ae2", "be1", "be2", "ce2", "de3"),
                    setOf("ae3", "be3", "ce3", "de4")
                ),
            ), queries3
        )
    }

    /**
     * A trace can get extended even after a completely new trace is created
     */
    @Test
    fun `v4 table 2 id c`() {
        testBase(
            setOf("eban", "eket", "ekko", "ekpo"), listOf(
                setOf(setOf("ae1", "be1")),
                setOf(setOf("ae1", "be1", "ce1", "de1"), setOf("ae1", "be1", "ce1", "de2")),
                setOf(
                    setOf("ae1", "be1", "ce1", "de1"),
                    setOf("ae1", "be1", "ce1", "de2"),
                    setOf("ae1", "be1", "ce2", "de3"),
                    setOf("be3")
                ),
                setOf(
                    setOf("ae1", "be1", "ce1", "de1"),
                    setOf("ae1", "be1", "ce1", "de2"),
                    setOf("ae1", "be1", "ce2", "de3"),
                    setOf("ae3", "be3", "ce3", "de4")
                ),
                setOf(
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "de2"),
                    setOf("ae1", "ae2", "be1", "be2", "ce2", "de3"),
                    setOf("ae3", "be3", "ce3", "de4")
                ),
            ), queries4
        )
    }

    /**
     * Based on Tables 1 and 2 in https://doi.org/10.1007/s10115-019-01430-6
     */
    private fun testBase(
        identifyingClasses: Set<String>,
        expectedTraces: List<Set<Set<String>>>,
        queries: List<String>
    ) {
        DriverManager.getConnection(containerJdbcURL).use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute("create table EBAN (id int primary key, text text)")
                stmt.execute("create table EKET (id int primary key, eban int references EBAN(id), text text)")
                stmt.execute("create table EKKO (id int primary key, eban int references EBAN(id), text text)")
                stmt.execute("create table EKPO (id int primary key, ekko int references EKKO(id), text text)")
            }
            val metaModelId = buildMetaModel(dataStoreDBName, "metaModelName", SchemaCrawlerExplorer(connection))

            val etlProcessId = UUID.randomUUID()

            val executor = processm.core.persistence.connection.transaction(dataStoreDBName) {
                val classes = MetaModelReader(metaModelId.value).getClassNames()
                val graph =
                    DefaultDirectedGraph<EntityID<Int>, Relationship>(Relationship::class.java)
                Relationships
                    .select { (Relationships.sourceClassId inList classes.keys) and (Relationships.targetClassId inList classes.keys) }
                    .forEach {
                        val r = Relationship.wrapRow(it)
                        graph.addVertex(r.sourceClass.id)
                        graph.addVertex(r.targetClass.id)
                        graph.addEdge(r.sourceClass.id, r.targetClass.id, r)
                    }
                val identifyingClassesIds =
                    classes.entries.filter { it.value in identifyingClasses }.mapToSet { it.key }
                AutomaticEtlProcessExecutor(
                    dataStoreDBName,
                    etlProcessId,
                    DAGBusinessPerspectiveDefinition(graph, identifyingClassesIds)
                )
            }

            val applier = mockk<LogGeneratingDatabaseChangeApplier> {
                every { this@mockk.dataStoreDBName } returns this@DebeziumChangeTrackerTest.dataStoreDBName
                every { this@mockk.metaModelId } returns metaModelId.value
                every { applyChange(any()) } answers { callOriginal() }
                every { getExecutorsForClass(any()) } returns listOf(executor)
            }

            DebeziumChangeTracker(
                debeziumConfiguration,
                applier,
                dataStoreId,
                UUID.randomUUID()
            ).use { tracker ->
                tracker.start()
                connection.autoCommit = false
                for ((query, expected) in queries zip expectedTraces) {
                    connection.createStatement().use { stmt ->
                        stmt.executeUpdate(query)
                    }
                    connection.commit()
                    Thread.sleep(1_000)
                    val xes = DBHierarchicalXESInputStream(
                        dataStoreDBName,
                        Query("where l:identity:id = $etlProcessId")
                    )
                    val actual = HashMap<UUID, HashMap<UUID, HashSet<String>>>()
                    for (log in xes) {
                        val logMap = actual.computeIfAbsent(log.identityId!!) { HashMap() }
                        for (trace in log.traces) {
                            val traceSet = logMap.computeIfAbsent(trace.identityId!!) { HashSet() }
                            trace.events.mapTo(traceSet) { it["db:text"].toString().trim('"') }
                        }
                    }
                    assertEquals(1, actual.size)
                    val traces = actual.values.single()
                    assertEquals(expected.size, traces.size)
                    assertEquals(expected, traces.values.toSet())
                }
            }
        }
    }
}