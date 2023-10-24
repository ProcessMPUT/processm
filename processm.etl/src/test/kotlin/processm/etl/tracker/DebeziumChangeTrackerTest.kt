package processm.etl.tracker

import org.jetbrains.exposed.dao.id.EntityID
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgroups.util.UUID
import processm.core.helpers.mapToSet
import processm.core.persistence.Migrator
import processm.core.persistence.connection.DatabaseChecker
import processm.core.persistence.connection.transaction
import processm.etl.discovery.SchemaCrawlerExplorer
import processm.etl.metamodel.DAGBusinessPerspectiveDefinition
import processm.etl.metamodel.MetaModel
import processm.etl.metamodel.MetaModelAppender
import processm.etl.metamodel.MetaModelReader
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.sql.DriverManager
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DebeziumChangeTrackerTest {

    private lateinit var targetDBname: String
    private lateinit var dataStoreDBName: String
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        targetDBname = UUID.randomUUID().toString()
        dataStoreDBName = UUID.randomUUID().toString()
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
    fun `table 2 id a`() {
        testBase(
            setOf("eban", "eket"), listOf(
                emptySet(),
                setOf(setOf(1, 2, 3, 4, 5, 6, 7)),
                setOf(setOf(1, 2, 3, 4, 5, 6, 7, 8, 9)),
                setOf(setOf(1, 2, 3, 4, 5, 6, 7, 8, 9), setOf(10, 11, 12, 13)),
            )
        )
    }

    @Test
    fun `table 2 id b`() {
        testBase(
            setOf("eban", "eket", "ekko"), listOf(
                emptySet(),
                setOf(setOf(1, 2, 3, 4, 5, 6, 7)),
                setOf(setOf(1, 2, 3, 4, 5, 6, 7), setOf(1, 2, 3, 4, 8, 9)),
                setOf(setOf(1, 2, 3, 4, 5, 6, 7), setOf(1, 2, 3, 4, 8, 9), setOf(10, 11, 12, 13)),
            )
        )
    }

    @Test
    fun `table 2 id c`() {
        testBase(
            setOf("eban", "eket", "ekko", "ekpo"), listOf(
                emptySet(),
                setOf(setOf(1, 2, 3, 4, 5, 6), setOf(1, 2, 3, 4, 5, 7)),
                setOf(setOf(1, 2, 3, 4, 5, 6), setOf(1, 2, 3, 4, 5, 7), setOf(1, 2, 3, 4, 8, 9)),
                setOf(setOf(1, 2, 3, 4, 5, 6), setOf(1, 2, 3, 4, 5, 7), setOf(1, 2, 3, 4, 8, 9), setOf(10, 11, 12, 13)),
            )
        )
    }

    /**
     * Based on Tables 1 and 2 in https://doi.org/10.1007/s10115-019-01430-6
     *
     * Tests calling this function are brittle, as they hardcode database IDs related to events generated by Debezium.
     * It is assumed the IDs start from 1 and are assigned in consecutive order.
     */
    private fun testBase(identifyingClasses: Set<String>, expectedTraces: List<Set<Set<Int>>>) {
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
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID()
            ).use { tracker ->

                val queries = listOf(
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
                tracker.start()
                connection.autoCommit = false
                for ((query, expected) in queries zip expectedTraces) {
                    connection.createStatement().use { stmt ->
                        stmt.executeUpdate(query)
                    }
                    connection.commit()
                    Thread.sleep(1_000)
                    assertEquals(expected, metaModel.buildTracesForBusinessPerspective(bussinessPerspective).toSet())
                }
            }
        }
    }
}