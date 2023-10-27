package processm.etl.tracker

import com.google.common.collect.HashBiMap
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultDirectedGraph
import processm.core.helpers.mapToSet
import processm.core.log.Log
import processm.core.log.Trace
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.persistence.Migrator
import processm.core.persistence.connection.DatabaseChecker
import processm.core.persistence.connection.transaction
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
import kotlin.collections.HashMap
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

    private lateinit var dataStoreId: UUID
    private lateinit var targetDBName: String
    private lateinit var dataStoreDBName: String
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        targetDBName = UUID.randomUUID().toString()
        dataStoreId = UUID.randomUUID()
        dataStoreDBName = dataStoreId.toString()
        tempDir = Files.createTempDirectory("processm").toFile()
        DriverManager.getConnection(DatabaseChecker.baseConnectionURL).use { connection ->
            connection.prepareStatement("CREATE DATABASE \"$targetDBName\"").use {
                it.execute()
            }
        }
        Migrator.migrate(dataStoreDBName)
    }

    @AfterTest
    fun cleanup() {
        DriverManager.getConnection(DatabaseChecker.baseConnectionURL).use { connection ->
            connection.prepareStatement("DROP DATABASE \"$targetDBName\"").use {
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
        //TODO this test makes no sense taking queries3 into account
        testBase(
            setOf("eban", "eket", "ekko", "ekpo"), listOf(
                setOf(setOf("ae1", "ae2", "be1", "be2")),
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
                emptySet(),
                setOf(setOf("ae1", "be1", "ce1", "de1"), setOf("ae1", "be1", "ce1", "de2")),
                setOf(
                    setOf("ae1", "be1", "ce1", "de1"),
                    setOf("ae1", "be1", "ce1", "de2"),
                    setOf("ae1", "be1", "ce2", "de3")
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
        DriverManager.getConnection(DatabaseChecker.switchDatabaseURL(targetDBName)).use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute("create table EBAN (id int primary key, text text)")
                stmt.execute("create table EKET (id int primary key, eban int references EBAN(id), text text)")
                stmt.execute("create table EKKO (id int primary key, eban int references EBAN(id), text text)")
                stmt.execute("create table EKPO (id int primary key, ekko int references EKKO(id), text text)")
            }
            val dataModelId = MetaModel.build(dataStoreDBName, "metaModelName", SchemaCrawlerExplorer(connection))
            val metaModelReader = MetaModelReader(dataModelId.value)

            val etlProcessId = UUID.randomUUID()


            val provider = transaction(dataStoreDBName) {
                val classes = metaModelReader.getClassNames()
                val stub = object : ETLProcessStub {

                    val traces = HashBiMap.create<Set<RemoteObjectID>, UUID>()
                    val objects = HashMap<RemoteObjectID, HashSet<UUID>>()
                    override val processId: UUID
                        get() = etlProcessId
                    override val identifyingClasses: Set<EntityID<Int>>
                        get() = classes.entries.filter { it.value in identifyingClasses }.mapToSet { it.key }
                    override val relevantClasses: Set<EntityID<Int>>
                        get() = classes.keys

//                    override fun getTrace(caseIdentifier: Set<RemoteObjectID>): UUID? = traces[caseIdentifier]
//
//                    override fun updateTrace(traceId: UUID, newCaseIdentifier: Set<RemoteObjectID>) {
//                        traces.inverse()[traceId] = newCaseIdentifier
//                        for (o in newCaseIdentifier)
//                            addObjectToTrace(traceId, o)
//                    }
//
//                    override fun createTrace(caseIdentifier: Set<RemoteObjectID>): UUID =
//                        UUID.randomUUID().also { updateTrace(it, caseIdentifier) }
//
//                    override fun getTracesWithObject(obj: RemoteObjectID): Sequence<UUID> =
//                        objects[obj].orEmpty().asSequence()
//
//                    override fun addObjectToTrace(traceId: UUID, obj: RemoteObjectID) {
//                        objects.computeIfAbsent(obj) { HashSet() }.add(traceId)
//                    }
//
//                    override fun createAnonymousTrace(): UUID = UUID.randomUUID()

                    override fun getRelevanceGraph(): Graph<EntityID<Int>, ETLProcessStub.Arc> {
                        val result =
                            DefaultDirectedGraph<EntityID<Int>, ETLProcessStub.Arc>(ETLProcessStub.Arc::class.java)
                        Relationships
                            .select { (Relationships.sourceClassId inList classes.keys) and (Relationships.targetClassId inList classes.keys) }
                            .forEach {
                                val r = Relationship.wrapRow(it)
                                result.addVertex(r.sourceClass.id)
                                result.addVertex(r.targetClass.id)
                                val arc = ETLProcessStub.Arc(
                                    r.sourceClass.id,
                                    r.referencingAttributesName.name,
                                    r.targetClass.id
                                )
                                result.addEdge(r.sourceClass.id, r.targetClass.id, arc)
                            }
                        return result
                    }

                }
                object : ETLProcessProvider {
                    override fun getProcessesForClass(className: String): List<ETLProcessStub> = listOf(stub)
                }
            }

            val metaModelAppender = MetaModelAppender(metaModelReader)
            val metaModel = MetaModel(dataStoreDBName, metaModelReader, metaModelAppender, provider)

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
//                    val intermediate = metaModel.buildTracesForBusinessPerspective(bussinessPerspective)
//                    val flatXES = MetaModelXESInputStream(intermediate, dataStoreDBName, dataModelId.value)
//                    val xes = HoneyBadgerHierarchicalXESInputStream(flatXES)
                    val xes = DBHierarchicalXESInputStream(
                        dataStoreDBName,
                        Query("where l:id = $etlProcessId")
                    )
//                    val log = xes.single()
//                    log.traces.forEach { trace ->
//                        println(trace.events.map { event ->
//                            event.attributes.entries.joinToString(
//                                prefix = "(",
//                                postfix = ")"
//                            ) { "${it.key}=`${it.value}`" }
//                        }.toList())
//                    }
                    val actual = HashMap<UUID, HashMap<UUID, HashSet<String>>>()
                    for (log in xes) {
                        val logMap = actual.computeIfAbsent(log.identityId!!) { HashMap() }
                        for (trace in log.traces) {
                            val traceSet = logMap.computeIfAbsent(trace.identityId!!) { HashSet() }
                            trace.events.mapTo(traceSet) { it["db:text"].toString().trim('"') }
                        }
                    }
                    for ((logId, log) in actual.entries) {
                        println(logId)
                        for ((traceId, trace) in log.entries) {
                            println("\t$traceId")
                            println("\t\t$trace")
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