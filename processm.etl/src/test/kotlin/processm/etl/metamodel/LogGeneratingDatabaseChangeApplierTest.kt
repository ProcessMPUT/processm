package processm.etl.metamodel

import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jgrapht.graph.DefaultDirectedGraph
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.models.*
import processm.etl.tracker.DatabaseChangeApplier
import processm.helpers.time.toLocalDateTime
import java.time.Instant
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun <E, A> traceEquals(expected: Collection<E>, actual: Collection<A>) =
    expected.size == actual.size && expected.toSet() == actual.toSet()

fun <T> assertLogEquals(expected: Collection<Collection<T>>, actual: Collection<Collection<T>>) {
    assertEquals(expected.size, actual.size)
    val tmp = LinkedList(actual)
    for (e in expected) {
        val i = tmp.iterator()
        var hit = false
        while (i.hasNext()) {
            val current = i.next()
            if (traceEquals(e, current)) {
                hit = true
                i.remove()
                break
            }
        }
        assertTrue { hit }
    }
    assertTrue { tmp.isEmpty() }
}

/**
 * Test based on https://link.springer.com/article/10.1007/s10115-019-01430-6, in particular Fig. 9, Tables 1 and 2
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LogGeneratingDatabaseChangeApplierTest {

    private var metaModelId: Int? = null
    private lateinit var eket: Class
    private lateinit var eban: Class
    private lateinit var ekko: Class
    private lateinit var ekpo: Class
    private val temporaryDB = UUID.randomUUID().toString()
    private lateinit var etlProcessId: UUID

    @BeforeTest
    fun setup() {
        transaction(DBCache.get(temporaryDB).database) {
            etlProcessId = EtlProcessMetadata.new {
                name = "test"
                processType = "automatic"
                dataConnector = DataConnector.new {
                    name = "test"
                    connectionProperties = ConnectionProperties(ConnectionType.JdbcString, "")
                }
            }.id.value
            // DataModel from Fig 9 and Table 1 in https://doi.org/10.1007/s10115-019-01430-6
            val dataModel = DataModel.new {
                name = "test"
                versionDate = Instant.now().toLocalDateTime()
            }
            metaModelId = dataModel.id.value
            eket = Class.new {
                this.dataModel = dataModel
                this.name = "EKET"
            }
            eban = Class.new {
                this.dataModel = dataModel
                this.name = "EBAN"
            }
            ekko = Class.new {
                this.dataModel = dataModel
                this.name = "EKKO"
            }
            ekpo = Class.new {
                this.dataModel = dataModel
                this.name = "EKPO"
            }
            Relationship.new {
                this.dataModel = dataModel
                relationshipName = "eket2eban"
                sourceClass = eket
                targetClass = eban
                referencingAttributesName = AttributesName.new {
                    name = "eban"
                    type = "type1"
                    attributeClass = eban
                    isReferencingAttribute = true
                }
            }
            Relationship.new {
                this.dataModel = dataModel
                relationshipName = "ekko2eban"
                sourceClass = ekko
                targetClass = eban
                referencingAttributesName = AttributesName.new {
                    name = "eban"
                    type = "type2"
                    attributeClass = eban
                    isReferencingAttribute = true
                }
            }
            Relationship.new {
                this.dataModel = dataModel
                relationshipName = "ekpo2ekko"
                sourceClass = ekpo
                targetClass = ekko
                referencingAttributesName = AttributesName.new {
                    name = "ekko"
                    type = "type3"
                    attributeClass = ekko
                    isReferencingAttribute = true
                }
            }

        }
    }


    @AfterAll
    fun cleanup() {
        DBCache.get(temporaryDB).close()
        DBCache.getMainDBPool().getConnection().use {
            it.prepareStatement("drop database \"$temporaryDB\" ").execute()
        }
    }


    private fun dbEvent(
        entityId: String,
        entityTable: String,
        eventName: String,
        vararg args: Pair<String, String>
    ): DatabaseChangeApplier.DatabaseChangeEvent {
        val attrs = mutableMapOf("text" to eventName)
        attrs.putAll(args)
        return DatabaseChangeApplier.DatabaseChangeEvent(
            "id",
            entityId,
            null,
            entityTable,
            null,
            null,
            DatabaseChangeApplier.EventType.Update,
            false,
            attrs
        )
    }

    private fun getApplier(identifyingClasses: Set<EntityID<Int>>): LogGeneratingDatabaseChangeApplier {
        val executor = processm.core.persistence.connection.transaction(temporaryDB) {
            val subquery = Classes.slice(Classes.id).select { Classes.dataModelId eq metaModelId }
            val graph =
                DefaultDirectedGraph<EntityID<Int>, Relationship>(Relationship::class.java)
            Relationships
                .select { (Relationships.sourceClassId inSubQuery subquery) and (Relationships.targetClassId inSubQuery subquery) }
                .forEach {
                    val r = Relationship.wrapRow(it)
                    graph.addVertex(r.sourceClass.id)
                    graph.addVertex(r.targetClass.id)
                    graph.addEdge(r.sourceClass.id, r.targetClass.id, r)
                }
            AutomaticEtlProcessExecutor(
                temporaryDB,
                etlProcessId,
                DAGBusinessPerspectiveDefinition(graph, identifyingClasses)
            )
        }

        val applier = mockk<LogGeneratingDatabaseChangeApplier> {
            every { dataStoreDBName } returns temporaryDB
            every { this@mockk.metaModelId } returns this@LogGeneratingDatabaseChangeApplierTest.metaModelId!!
            every { applyChange(any()) } answers { callOriginal() }
            every { getExecutorsForClass(any(), any()) } returns listOf(executor)
        }

        return applier
    }

    private fun readLog(logId: UUID): List<List<String>> {
        val xes = DBHierarchicalXESInputStream(temporaryDB, Query("where l:id = $logId"))
        val actual = HashMap<UUID, HashMap<UUID, ArrayList<String>>>()
        for (log in xes) {
            val traces = actual.computeIfAbsent(log.identityId!!) { HashMap() }
            for (trace in log.traces) {
                val events = traces.computeIfAbsent(trace.identityId!!) { ArrayList() }
                trace.events.mapTo(events) { it["db:text"].toString().trim('"') }
            }
        }
        return actual.values.flatMap { it.values }
    }

    @Test
    fun `v1 table 2 id b`() {
        val metaModel = getApplier(setOf(eket.id, eban.id, ekko.id))
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be1"),
                dbEvent("a1", "EKET", "ae1", "eban" to "b1"),
            )
        )
        assertLogEquals(setOf(setOf("ae1", "be1")), readLog(etlProcessId))
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be2"),
                dbEvent("a1", "EKET", "ae2"),
                dbEvent("c1", "EKKO", "ce1", "eban" to "b1"),
                dbEvent("d1", "EKPO", "de1", "ekko" to "c1"),
                dbEvent("d2", "EKPO", "de2", "ekko" to "c1"),
            )
        )
        assertLogEquals(setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2")), readLog(etlProcessId))
        metaModel.applyChange(
            listOf(
                dbEvent("c2", "EKKO", "ce2", "eban" to "b1"),
                dbEvent("d3", "EKPO", "de3", "ekko" to "c2")
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2"),
                setOf("ae1", "ae2", "be1", "be2", "ce2", "de3")
            ), readLog(etlProcessId)
        )
        metaModel.applyChange(
            listOf(
                dbEvent("b2", "EBAN", "be3"),
                dbEvent("a2", "EKET", "ae3", "eban" to "b2"),
                dbEvent("c3", "EKKO", "ce3", "eban" to "b2"),
                dbEvent("d4", "EKPO", "de4", "ekko" to "c3")
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2"),
                setOf("ae1", "ae2", "be1", "be2", "ce2", "de3"),
                setOf("ae3", "be3", "ce3", "de4")
            ), readLog(etlProcessId)
        )
    }

    @Test
    fun `v1 table 2 id a`() {
        val metaModel = getApplier(setOf(eket.id, eban.id))
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be1"),
                dbEvent("a1", "EKET", "ae1", "eban" to "b1"),
            )
        )
        assertLogEquals(setOf(setOf("ae1", "be1")), readLog(etlProcessId))
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be2"),
                dbEvent("a1", "EKET", "ae2"),
                dbEvent("c1", "EKKO", "ce1", "eban" to "b1"),
                dbEvent("d1", "EKPO", "de1", "ekko" to "c1"),
                dbEvent("d2", "EKPO", "de2", "ekko" to "c1"),
            )
        )
        assertLogEquals(setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2")), readLog(etlProcessId))
        metaModel.applyChange(
            listOf(
                dbEvent("c2", "EKKO", "ce2", "eban" to "b1"),
                dbEvent("d3", "EKPO", "de3", "ekko" to "c2")
            )
        )
        assertLogEquals(
            setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3")), readLog(etlProcessId)
        )
        metaModel.applyChange(
            listOf(
                dbEvent("b2", "EBAN", "be3"),
                dbEvent("a2", "EKET", "ae3", "eban" to "b2"),
                dbEvent("c3", "EKKO", "ce3", "eban" to "b2"),
                dbEvent("d4", "EKPO", "de4", "ekko" to "c3")
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "ae2", "be1", "be2", "ce1", "ce2", "de1", "de2", "de3"),
                setOf("ae3", "be3", "ce3", "de4")
            ), readLog(etlProcessId)
        )
    }

    @Test
    fun `v1 table 2 id c`() {
        val metaModel = getApplier(setOf(eket.id, eban.id, ekko.id, ekpo.id))
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be1"),
                dbEvent("a1", "EKET", "ae1", "eban" to "b1"),
            )
        )
        assertLogEquals(setOf(setOf("ae1", "be1")), readLog(etlProcessId))
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be2"),
                dbEvent("a1", "EKET", "ae2"),
                dbEvent("c1", "EKKO", "ce1", "eban" to "b1"),
                dbEvent("d1", "EKPO", "de1", "ekko" to "c1"),
                dbEvent("d2", "EKPO", "de2", "ekko" to "c1"),
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de2")
            ), readLog(etlProcessId)
        )
        metaModel.applyChange(
            listOf(
                dbEvent("c2", "EKKO", "ce2", "eban" to "b1"),
                dbEvent("d3", "EKPO", "de3", "ekko" to "c2")
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de2"),
                setOf("ae1", "ae2", "be1", "be2", "ce2", "de3")
            ), readLog(etlProcessId)
        )
        metaModel.applyChange(
            listOf(
                dbEvent("b2", "EBAN", "be3"),
                dbEvent("a2", "EKET", "ae3", "eban" to "b2"),
                dbEvent("c3", "EKKO", "ce3", "eban" to "b2"),
                dbEvent("d4", "EKPO", "de4", "ekko" to "c3")
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de2"),
                setOf("ae1", "ae2", "be1", "be2", "ce2", "de3"),
                setOf("ae3", "be3", "ce3", "de4")
            ), readLog(etlProcessId)
        )
    }

    @Test
    fun `v4 table 2 id c`() {
        val metaModel = getApplier(setOf(eket.id, eban.id, ekko.id, ekpo.id))
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be1"),
                dbEvent("a1", "EKET", "ae1", "eban" to "b1"),
            )
        )
        assertLogEquals(setOf(setOf("ae1", "be1")), readLog(etlProcessId))
        metaModel.applyChange(
            listOf(
                dbEvent("c1", "EKKO", "ce1", "eban" to "b1"),
                dbEvent("d1", "EKPO", "de1", "ekko" to "c1"),
                dbEvent("d2", "EKPO", "de2", "ekko" to "c1"),
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "be1", "ce1", "de1"),
                setOf("ae1", "be1", "ce1", "de2")
            ), readLog(etlProcessId)
        )
        metaModel.applyChange(
            listOf(
                dbEvent("c2", "EKKO", "ce2", "eban" to "b1"),
                dbEvent("d3", "EKPO", "de3", "ekko" to "c2"),
                dbEvent("b2", "EBAN", "be3"),
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "be1", "ce1", "de1"),
                setOf("ae1", "be1", "ce1", "de2"),
                setOf("ae1", "be1", "ce2", "de3"),
                setOf("be3")
            ), readLog(etlProcessId)
        )
        metaModel.applyChange(
            listOf(
                dbEvent("a2", "EKET", "ae3", "eban" to "b2"),
                dbEvent("c3", "EKKO", "ce3", "eban" to "b2"),
                dbEvent("d4", "EKPO", "de4", "ekko" to "c3")
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "be1", "ce1", "de1"),
                setOf("ae1", "be1", "ce1", "de2"),
                setOf("ae1", "be1", "ce2", "de3"),
                setOf("ae3", "be3", "ce3", "de4")
            ), readLog(etlProcessId)
        )
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be2"),
                dbEvent("a1", "EKET", "ae2"),
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de2"),
                setOf("ae1", "ae2", "be1", "be2", "ce2", "de3"),
                setOf("ae3", "be3", "ce3", "de4")
            ), readLog(etlProcessId)
        )
    }

    @Test
    fun `batch insert table 2 id c`() {
        val metaModel = getApplier(setOf(eket.id, eban.id, ekko.id, ekpo.id))
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be1"),
                dbEvent("a1", "EKET", "ae1", "eban" to "b1"),
                dbEvent("b1", "EBAN", "be2"),
                dbEvent("a1", "EKET", "ae2"),
                dbEvent("c1", "EKKO", "ce1", "eban" to "b1"),
                dbEvent("d1", "EKPO", "de1", "ekko" to "c1"),
                dbEvent("d2", "EKPO", "de2", "ekko" to "c1"),
                dbEvent("c2", "EKKO", "ce2", "eban" to "b1"),
                dbEvent("d3", "EKPO", "de3", "ekko" to "c2"),
                dbEvent("b2", "EBAN", "be3"),
                dbEvent("a2", "EKET", "ae3", "eban" to "b2"),
                dbEvent("c3", "EKKO", "ce3", "eban" to "b2"),
                dbEvent("d4", "EKPO", "de4", "ekko" to "c3")
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de2"),
                setOf("ae1", "ae2", "be1", "be2", "ce2", "de3"),
                setOf("ae3", "be3", "ce3", "de4")
            ), readLog(etlProcessId)
        )
    }

    @Test
    fun `batch insert with disabled constraints table 2 id c`() {
        val metaModel = getApplier(setOf(eket.id, eban.id, ekko.id, ekpo.id))
        metaModel.applyChange(
            listOf(
                dbEvent("d4", "EKPO", "de4", "ekko" to "c3"),
                dbEvent("d3", "EKPO", "de3", "ekko" to "c2"),
                dbEvent("d2", "EKPO", "de2", "ekko" to "c1"),
                dbEvent("d1", "EKPO", "de1", "ekko" to "c1"),
                dbEvent("c3", "EKKO", "ce3", "eban" to "b2"),
                dbEvent("c2", "EKKO", "ce2", "eban" to "b1"),
                dbEvent("c1", "EKKO", "ce1", "eban" to "b1"),
                dbEvent("a1", "EKET", "ae1", "eban" to "b1"),
                dbEvent("a1", "EKET", "ae2"),
                dbEvent("a2", "EKET", "ae3", "eban" to "b2"),
                dbEvent("b1", "EBAN", "be1"),
                dbEvent("b1", "EBAN", "be2"),
                dbEvent("b2", "EBAN", "be3"),
            )
        )
        assertLogEquals(
            setOf(
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de2"),
                setOf("ae1", "ae2", "be1", "be2", "ce2", "de3"),
                setOf("ae3", "be3", "ce3", "de4")
            ), readLog(etlProcessId)
        )
    }

    @Test
    fun `batch insert with disabled constraints simplified`() {
        val metaModel = getApplier(setOf(eket.id, eban.id, ekko.id, ekpo.id))
        metaModel.applyChange(
            listOf(
                dbEvent("d1", "EKPO", "de1", "ekko" to "c1"),
                dbEvent("a1", "EKET", "ae1", "eban" to "b1"),
                dbEvent("c1", "EKKO", "ce1", "eban" to "b1"),
                dbEvent("b1", "EBAN", "be1"),
            )
        )
        assertLogEquals(setOf(setOf("ae1", "be1", "ce1", "de1")), readLog(etlProcessId))
    }

    @Test
    fun `batch insert with disabled constraints simplified with multiple events per entity`() {
        val metaModel = getApplier(setOf(eket.id, eban.id, ekko.id, ekpo.id))
        metaModel.applyChange(
            listOf(
                dbEvent("d1", "EKPO", "de1", "ekko" to "c1"),
                dbEvent("a1", "EKET", "ae1", "eban" to "b1"),
                dbEvent("a1", "EKET", "ae2"),
                //Processing be3 succeeds and causes processing of the postponed events
                dbEvent("b2", "EBAN", "be3"),
                dbEvent("a1", "EKET", "ae3"),
                dbEvent("c1", "EKKO", "ce1", "eban" to "b1"),
                dbEvent("b1", "EBAN", "be1"),
                dbEvent("b1", "EBAN", "be2"),
            )
        )
        assertLogEquals(
            setOf(setOf("ae1", "ae2", "ae3", "be1", "be2", "ce1", "de1"), setOf("be3")),
            readLog(etlProcessId)
        )
    }

    @Test
    fun `test getProcessesForClass`() {

    }


    //    @Ignore("Not a test")
    @Test
    fun `performance test`() {
        val n = 50
        val na = 3
        val nc = 2
        val nd = 4
        // Generates n*(na+nc*nd) events
        var aSeq = 1
        var cSeq = 1
        var dSeq = 1
        val metaModel = getApplier(setOf(eket.id, eban.id, ekko.id, ekpo.id))
        for (bId in 1..n) {
            val events = ArrayList<DatabaseChangeApplier.DatabaseChangeEvent>()
            with(events) {
                val bDbId = "b$bId"
                add(dbEvent(bDbId, "EBAN", "be$bId"))
                for (a in 1..na) {
                    val aId = aSeq++
                    add(dbEvent("a$aId", "EKET", "ae$aId", "eban" to bDbId))
                }
                for (c in 1..nc) {
                    val cId = cSeq++
                    val cDbId = "c$cId"
                    add(dbEvent(cDbId, "EKKO", "ce$cId", "eban" to bDbId))
                    for (d in 1..nd) {
                        val dId = dSeq++
                        add(dbEvent("d$dId", "EKPO", "de$dId", "ekko" to cDbId))
                    }
                }
            }
            metaModel.applyChange(events)
        }
    }
}
