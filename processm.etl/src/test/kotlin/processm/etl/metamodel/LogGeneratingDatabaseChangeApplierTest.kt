package processm.etl.metamodel

import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultDirectedGraph
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import processm.core.helpers.toLocalDateTime
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.models.*
import processm.etl.tracker.DatabaseChangeApplier
import java.time.Instant
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test based on https://link.springer.com/article/10.1007/s10115-019-01430-6, in particular Fig. 9, Tables 1 and 2
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(InMemoryXESProcessing::class)
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
        etlProcessId = UUID.randomUUID()
        transaction(DBCache.get(temporaryDB).database) {
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
                name = "eket2eban"
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
                name = "ekko2eban"
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
                name = "ekpo2ekko"
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
//        DBCache.get(temporaryDB).close()
//        DBCache.getMainDBPool().getConnection().use {
//            it.prepareStatement("drop database \"$temporaryDB\" ").execute()
//        }
        println(temporaryDB)
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
            val classes = MetaModelReader(metaModelId!!).getClassNames()
            val graph =
                DefaultDirectedGraph<EntityID<Int>, Arc>(Arc::class.java)
            Relationships
                .select { (Relationships.sourceClassId inList classes.keys) and (Relationships.targetClassId inList classes.keys) }
                .forEach {
                    val r = Relationship.wrapRow(it)
                    graph.addVertex(r.sourceClass.id)
                    graph.addVertex(r.targetClass.id)
                    val arc = Arc(
                        r.sourceClass.id,
                        r.referencingAttributesName.name,
                        r.targetClass.id
                    )
                    graph.addEdge(r.sourceClass.id, r.targetClass.id, arc)
                }
            AutomaticEtlProcessExecutor(temporaryDB, etlProcessId, graph, identifyingClasses)
        }

        val applier = mockk<LogGeneratingDatabaseChangeApplier> {
            every { dataStoreDBName } returns temporaryDB
            every { this@mockk.metaModelId } returns this@LogGeneratingDatabaseChangeApplierTest.metaModelId!!
            every { applyChange(any()) } answers { callOriginal() }
            every { getExecutorsForClass(any()) } returns listOf(executor)
        }

        return applier
    }

    private fun readLog(logId: UUID): Set<Set<String>> {
        val xes = DBHierarchicalXESInputStream(temporaryDB, Query("where l:id = $logId"))
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
        return actual.values.flatMap { it.values }.toSet()
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
        assertEquals(setOf(setOf("ae1", "be1")), readLog(etlProcessId))
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be2"),
                dbEvent("a1", "EKET", "ae2"),
                dbEvent("c1", "EKKO", "ce1", "eban" to "b1"),
                dbEvent("d1", "EKPO", "de1", "ekko" to "c1"),
                dbEvent("d2", "EKPO", "de2", "ekko" to "c1"),
            )
        )
        assertEquals(setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2")), readLog(etlProcessId))
        metaModel.applyChange(
            listOf(
                dbEvent("c2", "EKKO", "ce2", "eban" to "b1"),
                dbEvent("d3", "EKPO", "de3", "ekko" to "c2")
            )
        )
        assertEquals(
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
        assertEquals(
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
        assertEquals(setOf(setOf("ae1", "be1")), readLog(etlProcessId))
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be2"),
                dbEvent("a1", "EKET", "ae2"),
                dbEvent("c1", "EKKO", "ce1", "eban" to "b1"),
                dbEvent("d1", "EKPO", "de1", "ekko" to "c1"),
                dbEvent("d2", "EKPO", "de2", "ekko" to "c1"),
            )
        )
        assertEquals(setOf(setOf("ae1", "ae2", "be1", "be2", "ce1", "de1", "de2")), readLog(etlProcessId))
        metaModel.applyChange(
            listOf(
                dbEvent("c2", "EKKO", "ce2", "eban" to "b1"),
                dbEvent("d3", "EKPO", "de3", "ekko" to "c2")
            )
        )
        assertEquals(
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
        assertEquals(
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
        assertEquals(setOf(setOf("ae1", "be1")), readLog(etlProcessId))
        metaModel.applyChange(
            listOf(
                dbEvent("b1", "EBAN", "be2"),
                dbEvent("a1", "EKET", "ae2"),
                dbEvent("c1", "EKKO", "ce1", "eban" to "b1"),
                dbEvent("d1", "EKPO", "de1", "ekko" to "c1"),
                dbEvent("d2", "EKPO", "de2", "ekko" to "c1"),
            )
        )
        assertEquals(
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
        assertEquals(
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
        assertEquals(
            setOf(
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de1"),
                setOf("ae1", "ae2", "be1", "be2", "ce1", "de2"),
                setOf("ae1", "ae2", "be1", "be2", "ce2", "de3"),
                setOf("ae3", "be3", "ce3", "de4")
            ), readLog(etlProcessId)
        )
    }
}