package processm.etl.tracker

import org.jetbrains.exposed.dao.id.EntityID
import org.jgrapht.graph.DefaultDirectedGraph
import processm.core.helpers.mapToSet
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.persistence.Migrator
import processm.core.persistence.connection.DatabaseChecker
import processm.core.persistence.connection.transaction
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

@OptIn(InMemoryXESProcessing::class)
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

    private lateinit var dataStoreId: UUID
    private lateinit var targetDBname: String
    private lateinit var dataStoreDBName: String
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        targetDBname = UUID.randomUUID().toString()
        dataStoreId = UUID.randomUUID()
        dataStoreDBName = dataStoreId.toString()
        tempDir = Files.createTempDirectory("processm").toFile()
        DriverManager.getConnection(DatabaseChecker.baseConnectionURL).use { connection ->
            connection.prepareStatement("CREATE DATABASE \"$targetDBname\"").use {
                it.execute()
            }
        }
        Migrator.migrate(dataStoreDBName)
    }

    @AfterTest
    fun cleanup() {
        DriverManager.getConnection(DatabaseChecker.baseConnectionURL).use { connection ->
            connection.prepareStatement("DROP DATABASE \"$targetDBname\"").use {
                it.execute()
            }
            // TODO I wanna drop that DB, but something keeps using it
//            connection.prepareStatement("DROP DATABASE \"$dataStoreDBName\"").use {
//                it.execute()
//            }
        }
        tempDir.deleteRecursively()
    }

    private val debeziumConfiguration: Properties
        get() = Properties().apply {
            val databaseURL =
                URI(DatabaseChecker.baseConnectionURL.substring(5))   //skip jdbc: as URI seems to break on a protocol with multiple colons
            val query = databaseURL.query.split('&').associate {
                val list = it.split('=', limit = 2)
                list[0].lowercase() to list[1]
            }
            setProperty("name", "engine")
            setProperty("plugin.name", "pgoutput")  // TODO I think this should be the default in the main code as well
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
            setProperty("database.user", query["user"])
            setProperty("database.password", query["password"])
            setProperty("database.dbname", targetDBname)
            // properties for the testing environment; in particular the doc says that "slot.drop.on.stop" should be set on true only in test/development envs
            setProperty("slot.drop.on.stop", "true")
            setProperty("slot.name", UUID.randomUUID().toString().replace("-", "").lowercase())
        }


    @Test
    fun `v1 table 2 id a`() {
        testBase(
            setOf("eban", "eket"), listOf(
                emptySet(),
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
                emptySet(),
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
                emptySet(),
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
                emptySet(), //be1
                emptySet(), //ae1
                emptySet(), //be2
                emptySet(), //ae2
                emptySet(), //ce1
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1")), //de1
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2")), //de2
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2")), //ce2
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3")), //de3
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3")), //be3
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3")), //ae3
                setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3")), //ce3
                setOf(
                    setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3"),
                    setOf("ae3", "be3", "ce3", "de4")
                ), //de4
            ), queries2
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
        DriverManager.getConnection(DatabaseChecker.switchDatabaseURL(targetDBname)).use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute("create table EBAN (id int primary key, text text)")
                stmt.execute("create table EKET (id int primary key, eban int references EBAN(id), text text)")
                stmt.execute("create table EKKO (id int primary key, eban int references EBAN(id), text text)")
                stmt.execute("create table EKPO (id int primary key, ekko int references EKKO(id), text text)")
            }
            val dataModelId = MetaModel.build(dataStoreDBName, "metaModelName", SchemaCrawlerExplorer(connection))
            val metaModelReader = MetaModelReader(dataModelId.value)

            val bussinessPerspective = transaction(dataStoreDBName) {
                val classes = metaModelReader.getClassNames()
                val classesGraph = DefaultDirectedGraph<EntityID<Int>, String>(String::class.java).apply {
                    metaModelReader.getClassNames().keys.forEach(this::addVertex)
                    metaModelReader.getRelationships().forEach { r ->
                        addEdge(r.value.first, r.value.second, r.key)
                    }
                }
                DAGBusinessPerspectiveDefinition(classesGraph) {
                    classes.entries.filter { it.value in identifyingClasses }.mapToSet { it.key }.also { println(it) }
                }
            }

            val metaModelAppender = MetaModelAppender(metaModelReader)
            val metaModel = MetaModel(dataStoreDBName, metaModelReader, metaModelAppender)

            DebeziumChangeTracker(
                debeziumConfiguration,
                metaModel,
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
                    val intermediate = metaModel.buildTracesForBusinessPerspective(bussinessPerspective)
                    val flatXES = MetaModelXESInputStream(intermediate, dataStoreDBName, dataModelId.value)
                    val xes = HoneyBadgerHierarchicalXESInputStream(flatXES)
                    val log = xes.single()
                    val actual: Set<Set<String>> =
                        log.traces.mapToSet { trace -> trace.events.mapToSet { it["db:text"].toString().trim('"') } }
                    assertEquals(expected.size, actual.size)
                    assertEquals(expected, actual)
                }
            }
        }
    }
}