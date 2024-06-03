package processm.enhancement

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import processm.conformance.alignment
import processm.conformance.models.alignments.Alignment
import processm.core.DBTestHelper
import processm.core.TopicObserver
import processm.core.communication.Producer
import processm.core.esb.Artemis
import processm.core.esb.ServiceStatus
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.persistence.DurablePersistenceProvider
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import processm.enhancement.kpi.AlignerKPIServiceTests
import processm.enhancement.kpi.Report
import processm.helpers.map2d.DoublingMap2D
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Timeout(5L, unit = TimeUnit.SECONDS)
class DriftDetectorServiceTest {

    companion object {
        val dataStore = UUID.fromString(DBTestHelper.dbName)
        lateinit var persistenceProvider: DurablePersistenceProvider
        val artemis = Artemis()
        val wctObserver = TopicObserver(
            topic = WORKSPACE_COMPONENTS_TOPIC,
            filter = "$WORKSPACE_COMPONENT_EVENT = '${WorkspaceComponentEventType.ConceptDriftDetected}' OR ($WORKSPACE_COMPONENT_EVENT = '${WorkspaceComponentEventType.DataChange}' AND $WORKSPACE_COMPONENT_EVENT_DATA = '$DATA_CHANGE_CONCEPT_DRIFT')"
        )
        var modelId: Long = -1L
        val reports = List(2) { URI("processm.test:DriftDetectorServiceTest:$it") }

        @JvmStatic
        @BeforeAll
        fun setUp() {
            persistenceProvider = DurablePersistenceProvider(DBTestHelper.dbName)

            modelId = createModel()

            artemis.register()
            artemis.start()
            wctObserver.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            wctObserver.close()
            artemis.stop()
            DBSerializer.delete(DBCache.get(dataStore.toString()).database, modelId.toInt())
            reports.forEach { runCatching { persistenceProvider.delete(it) } }
            persistenceProvider.close()
        }

        fun createModel(): Long {
            val a = Node("a")
            val b = Node("b")
            val cnet = causalnet {
                start splits a
                a splits b
                b splits end
                start joins a
                a joins b
                b joins end
            }

            return DBSerializer.insert(DBCache.get(AlignerKPIServiceTests.dataStore.toString()).database, cnet).toLong()
        }

    }

    /**
     * Creates a workspace component and triggers a JMS event about creation.
     */
    fun createComponent(_query: String, _modelId: Long): UUID {
        val component = transactionMain {
            WorkspaceComponent.new {
                name = "test-aligner-kpi"
                componentType = ComponentTypeDto.CausalNet
                dataStoreId = dataStore
                query = _query
                data = ProcessModelComponentData(this).apply {
                    addModel(1, _modelId.toString())
                    acceptedModelVersion = 1
                }.toJSON()
                workspace = Workspace.all().firstOrNull() ?: Workspace.new { name = "test-workspace" }
            }
        }

        return component.id.value
    }

    fun addAlignments(componentId: UUID, reportURI: URI, alignments: List<Alignment>) {
        transactionMain {
            persistenceProvider.put(
                reportURI,
                Report(emptyMap(), emptyMap(), DoublingMap2D(), DoublingMap2D(), alignments)
            )
            WorkspaceComponent[componentId].apply {
                data = ProcessModelComponentData(this).apply {
                    val modelVersion = acceptedModelVersion!!
                    val dataVersion =
                        alignmentKPIReports[modelVersion]?.maxOf { it.key }?.let { it + 1 } ?: modelVersion
                    addAlignmentKPIReport(
                        modelVersion,
                        dataVersion,
                        reportURI
                    )

                    afterCommit {
                        triggerEvent(
                            Producer(),
                            event = WorkspaceComponentEventType.NewAlignments
                        ) {
                            setLong(MODEL_VERSION, modelVersion)
                            setLong(DATA_VERSION, dataVersion)
                        }
                    }
                }.toJSON()
            }
        }

    }

    @Test
    fun test() {
        val driftDetectorService = DriftDetectorService()
        try {
            driftDetectorService.register()
            driftDetectorService.start()
            assertEquals(ServiceStatus.Started, driftDetectorService.status)
            val componentId = createComponent("", modelId)
            val alignments = listOf(
                alignment {
                    "a" executing "a"
                    "b" executing "b"
                },
                alignment {
                    null executing "a"
                    null executing "b"
                    "c" executing null
                    "d" executing null
                    cost = 4
                },
            )
            addAlignments(componentId, reports[0], List(10) { alignments[0] })
            transactionMain {
                assertFalse { ProcessModelComponentData(WorkspaceComponent[componentId]).hasConceptDrift }
            }
            addAlignments(componentId, reports[1], List(10) { alignments[0] } + List(10) { alignments[1] })
            repeat(2) { wctObserver.waitForMessage() }
            transactionMain {
                assertTrue { ProcessModelComponentData(WorkspaceComponent[componentId]).hasConceptDrift }
            }
        } finally {
            driftDetectorService.stop()
        }
    }
}