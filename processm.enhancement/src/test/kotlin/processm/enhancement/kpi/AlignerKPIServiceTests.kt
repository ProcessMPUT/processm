package processm.enhancement.kpi

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import processm.core.DBTestHelper
import processm.core.TopicObserver
import processm.core.communication.Producer
import processm.core.esb.Artemis
import processm.core.esb.ServiceStatus
import processm.core.log.AppendingDBXESOutputStream
import processm.core.log.DBXESInputStream
import processm.core.log.DBXESOutputStream
import processm.core.log.Log
import processm.core.log.attribute.Attribute.COST_TOTAL
import processm.core.log.attribute.Attribute.IDENTITY_ID
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.persistence.DurablePersistenceProvider
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.core.persistence.get
import processm.core.querylanguage.Query
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.*

@Timeout(10L, unit = TimeUnit.SECONDS)
class AlignerKPIServiceTests {
    companion object {

        val dataStore = UUID.fromString(DBTestHelper.dbName)
        lateinit var persistenceProvider: DurablePersistenceProvider
        val logUUID = DBTestHelper.JournalReviewExtra
        val artemis = Artemis()
        val wctObserver = TopicObserver(
            topic = WORKSPACE_COMPONENTS_TOPIC,
            filter = "$WORKSPACE_COMPONENT_EVENT = '${WorkspaceComponentEventType.DataChange}' AND $WORKSPACE_COMPONENT_EVENT_DATA <> '${DataChangeType.Model}' AND $WORKSPACE_COMPONENT_EVENT_DATA <> '${DataChangeType.InitialModel}'"
        )
        var perfectCNetId: Long = -1L
        var mainstreamCNetId: Long = -1L

        @JvmStatic
        @BeforeAll
        fun setUp() {
            persistenceProvider = DurablePersistenceProvider(DBTestHelper.dbName)

            perfectCNetId = createPerfectCNet()
            mainstreamCNetId = createMainstreamCNet()

            artemis.register()
            artemis.start()
            wctObserver.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            wctObserver.close()
            artemis.stop()
            DBSerializer.delete(DBCache.get(dataStore.toString()).database, perfectCNetId.toInt())
            DBSerializer.delete(DBCache.get(dataStore.toString()).database, mainstreamCNetId.toInt())
            persistenceProvider.close()
        }

        fun createPerfectCNet(): Long {
            val inviteReviewers = Node("invite reviewers")
            val _beforeReview1 = Node("_before review 1", isSilent = true)
            val _beforeReview2 = Node("_before review 2", isSilent = true)
            val _beforeReview3 = Node("_before review 3", isSilent = true)
            val getReview1 = Node("get review 1")
            val getReview2 = Node("get review 2")
            val getReview3 = Node("get review 3")
            val getReviewX = Node("get review X")
            val timeOut1 = Node("time-out 1")
            val timeOut2 = Node("time-out 2")
            val timeOut3 = Node("time-out 3")
            val timeOutX = Node("time-out X")
            val _afterReview1 = Node("_after review 1", isSilent = true)
            val _afterReview2 = Node("_after review 2", isSilent = true)
            val _afterReview3 = Node("_after review 3", isSilent = true)
            val collect = Node("collect reviews")
            val decide = Node("decide")
            val _afterDecide = Node("_after decide", isSilent = true)
            val inviteAdditionalReviewer = Node("invite additional reviewer")
            val accept = Node("accept")
            val reject = Node("reject")
            val _end = Node("_end", isSilent = true)
            val cnet = causalnet {
                start = inviteReviewers
                end = _end

                inviteReviewers splits _beforeReview1 + _beforeReview2 + _beforeReview3

                inviteReviewers joins _beforeReview1
                inviteReviewers joins _beforeReview2
                inviteReviewers joins _beforeReview3
                _beforeReview1 splits getReview1 or timeOut1
                _beforeReview2 splits getReview2 or timeOut2
                _beforeReview3 splits getReview3 or timeOut3

                _beforeReview1 joins getReview1
                _beforeReview1 joins timeOut1
                _beforeReview2 joins getReview2
                _beforeReview2 joins timeOut2
                _beforeReview3 joins getReview3
                _beforeReview3 joins timeOut3

                getReview1 splits _afterReview1
                timeOut1 splits _afterReview1
                getReview2 splits _afterReview2
                timeOut2 splits _afterReview2
                getReview3 splits _afterReview3
                timeOut3 splits _afterReview3
                getReview1 or timeOut1 join _afterReview1
                getReview2 or timeOut2 join _afterReview2
                getReview3 or timeOut3 join _afterReview3

                _afterReview1 splits collect
                _afterReview2 splits collect
                _afterReview3 splits collect
                _afterReview1 + _afterReview2 + _afterReview3 join collect

                collect splits decide
                collect joins decide

                decide splits _afterDecide
                decide or getReviewX or timeOutX join _afterDecide

                _afterDecide splits inviteAdditionalReviewer or accept or reject
                _afterDecide joins inviteAdditionalReviewer
                _afterDecide joins accept
                _afterDecide joins reject

                inviteAdditionalReviewer splits getReviewX or timeOutX
                inviteAdditionalReviewer joins getReviewX
                inviteAdditionalReviewer joins timeOutX
                getReviewX splits _afterDecide
                timeOutX splits _afterDecide

                accept splits _end
                accept joins _end
                reject splits _end
                reject joins _end
            }

            return DBSerializer.insert(DBCache.get(dataStore.toString()).database, cnet).toLong()
        }

        fun createMainstreamCNet(): Long {
            val inviteReviewers = Node("invite reviewers")
            val _beforeReview1 = Node("_before review 1", isSilent = true)
            val _beforeReview2 = Node("_before review 2", isSilent = true)
            val _beforeReview3 = Node("_before review 3", isSilent = true)
            val getReview1 = Node("get review 1")
            val getReview2 = Node("get review 2")
            val getReview3 = Node("get review 3")
            val timeOut1 = Node("time-out 1")
            val timeOut2 = Node("time-out 2")
            val timeOut3 = Node("time-out 3")
            val _afterReview1 = Node("_after review 1", isSilent = true)
            val _afterReview2 = Node("_after review 2", isSilent = true)
            val _afterReview3 = Node("_after review 3", isSilent = true)
            val collect = Node("collect reviews")
            val decide = Node("decide")
            val accept = Node("accept")
            val reject = Node("reject")
            val _end = Node("_end", isSilent = true)
            val cnet = causalnet {
                start = inviteReviewers
                end = _end

                inviteReviewers splits _beforeReview1 + _beforeReview2 + _beforeReview3

                inviteReviewers joins _beforeReview1
                inviteReviewers joins _beforeReview2
                inviteReviewers joins _beforeReview3
                _beforeReview1 splits getReview1 or timeOut1
                _beforeReview2 splits getReview2 or timeOut2
                _beforeReview3 splits getReview3 or timeOut3

                _beforeReview1 joins getReview1
                _beforeReview1 joins timeOut1
                _beforeReview2 joins getReview2
                _beforeReview2 joins timeOut2
                _beforeReview3 joins getReview3
                _beforeReview3 joins timeOut3

                getReview1 splits _afterReview1
                timeOut1 splits _afterReview1
                getReview2 splits _afterReview2
                timeOut2 splits _afterReview2
                getReview3 splits _afterReview3
                timeOut3 splits _afterReview3
                getReview1 or timeOut1 join _afterReview1
                getReview2 or timeOut2 join _afterReview2
                getReview3 or timeOut3 join _afterReview3

                _afterReview1 splits collect
                _afterReview2 splits collect
                _afterReview3 splits collect
                _afterReview1 + _afterReview2 + _afterReview3 join collect

                collect splits decide
                collect joins decide

                decide splits accept or reject
                decide joins accept
                decide joins reject

                accept splits _end
                accept joins _end
                reject splits _end
                reject joins _end
            }

            return DBSerializer.insert(DBCache.get(dataStore.toString()).database, cnet).toLong()
        }
    }

    private var queueDelayMsOriginalValue: String? = null

    @BeforeTest
    fun before() {
        queueDelayMsOriginalValue = System.getProperty(AlignerKPIService.QUEUE_DELAY_MS_PROPERTY)
        System.setProperty(AlignerKPIService.QUEUE_DELAY_MS_PROPERTY, "1")
        wctObserver.reset()
    }

    @AfterTest
    fun after() {
        queueDelayMsOriginalValue?.let { System.setProperty(AlignerKPIService.QUEUE_DELAY_MS_PROPERTY, it) }
            ?: System.clearProperty(AlignerKPIService.QUEUE_DELAY_MS_PROPERTY)
    }

    /**
     * Creates a workspace component and triggers a JMS event about creation.
     *
     * Remark: Make sure to call this function after starting the first instance of [AlignerKPIService] to guarantee
     * that durable JMS topic consumer was registered before sending the first message.
     */
    fun createComponent(_query: String, _modelId: Long): UUID {
        val component = transactionMain {
            WorkspaceComponent.new {
                name = "test-aligner-kpi"
                componentType = ComponentTypeDto.CausalNet
                dataStoreId = dataStore
                query = _query
                data = ProcessModelComponentData.create(this).apply {
                    addModel(1, _modelId.toString())
                    acceptedModelVersion = 1
                }.toJSON()
                workspace = Workspace.all().firstOrNull() ?: Workspace.new { name = "test-workspace" }
            }
        }

        component.triggerEvent(
            Producer(),
            event = WorkspaceComponentEventType.ModelAccepted
        )
        return component.id.value
    }

    @AfterTest
    fun deleteComponent() {
        transactionMain {
            WorkspaceComponents.deleteWhere {
                (name eq "test-aligner-kpi") and (dataStoreId eq dataStore)
            }
        }
    }

    @Test
    fun `create KPI component based on the perfect model then run service`() {
        val alignerKpiService = AlignerKPIService()
        try {
            alignerKpiService.register()
            alignerKpiService.start()
            assertEquals(ServiceStatus.Started, alignerKpiService.status)

            createComponent(
                "select l:*, t:*, e:name, max(e:timestamp)-min(e:timestamp) where l:id=$logUUID group by e:name, e:instance",
                perfectCNetId
            )

            wctObserver.waitForMessage()
        } finally {
            alignerKpiService.stop()
        }

        transactionMain {
            val component = WorkspaceComponent.find {
                WorkspaceComponents.dataStoreId eq dataStore
            }.first()

            val report =
                persistenceProvider.get<Report>(ProcessModelComponentData.create(component).alignmentKPIReports.values.single().values.single())
            assertEquals(1, report.logKPI.size)
            assertEquals(6, report.traceKPI.size)
            assertEquals(20.0, report.traceKPI[COST_TOTAL]!!.median)
            assertEquals(50, report.traceKPI[COST_TOTAL]!!.raw.size)

            with(report.eventKPI.getRow("max(event:time:timestamp) - min(event:time:timestamp)")) {
                // instant activities
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "get review 1" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "get review 2" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "get review 3" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "get review X" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "time-out 1" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "time-out 2" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "time-out 3" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "time-out X" }.value.max)

                // longer activities [times in days]
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "invite reviewers" }.value.min)
                assertEquals(3.0, entries.first { (k, _) -> k?.name == "invite reviewers" }.value.median)
                assertEquals(12.0, entries.first { (k, _) -> k?.name == "invite reviewers" }.value.max)
                assertEquals(101, entries.first { (k, _) -> k?.name == "invite reviewers" }.value.raw.size)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "decide" }.value.min)
                assertEquals(3.0, entries.first { (k, _) -> k?.name == "decide" }.value.median)
                assertEquals(12.0, entries.first { (k, _) -> k?.name == "decide" }.value.max)
                assertEquals(100, entries.first { (k, _) -> k?.name == "decide" }.value.raw.size)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "invite additional reviewer" }.value.min)
                assertEquals(2.0, entries.first { (k, _) -> k?.name == "invite additional reviewer" }.value.median)
                assertEquals(11.0, entries.first { (k, _) -> k?.name == "invite additional reviewer" }.value.max)
                assertEquals(90, entries.first { (k, _) -> k?.name == "invite additional reviewer" }.value.raw.size)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "accept" }.value.min)
                assertEquals(1.0, entries.first { (k, _) -> k?.name == "accept" }.value.median)
                assertEquals(12.0, entries.first { (k, _) -> k?.name == "accept" }.value.max)
                assertEquals(45, entries.first { (k, _) -> k?.name == "accept" }.value.raw.size)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "reject" }.value.min)
                assertEquals(4.0, entries.first { (k, _) -> k?.name == "reject" }.value.median)
                assertEquals(9.0, entries.first { (k, _) -> k?.name == "reject" }.value.max)
                assertEquals(55, entries.first { (k, _) -> k?.name == "reject" }.value.raw.size)
            }
            assertNotNull(component.dataLastModified)
            assertNull(component.lastError)
        }
    }

    @Test
    fun `run service then create KPI component based on the mainstream model`() {
        val alignerKpiService = AlignerKPIService()
        try {
            alignerKpiService.register()
            alignerKpiService.start()
            assertEquals(ServiceStatus.Started, alignerKpiService.status)

            val componentId = createComponent(
                "select l:*, t:*, e:name, max(e:timestamp)-min(e:timestamp) where l:id=$logUUID group by e:name, e:instance",
                mainstreamCNetId
            )

            wctObserver.waitForMessage()
        } finally {
            alignerKpiService.stop()
        }

        transactionMain {
            val component = WorkspaceComponent.find {
                (WorkspaceComponents.name eq "test-aligner-kpi") and (WorkspaceComponents.dataStoreId eq dataStore)
            }.first()

            val report =
                persistenceProvider.get<Report>(ProcessModelComponentData.create(component).alignmentKPIReports.values.single().values.single())
            assertEquals(1, report.logKPI.size)
            assertEquals(6, report.traceKPI.size)
            assertEquals(20.0, report.traceKPI[COST_TOTAL]!!.median)
            assertEquals(50, report.traceKPI[COST_TOTAL]!!.raw.size)

            with(report.eventKPI.getRow("max(event:time:timestamp) - min(event:time:timestamp)")) {
                // missing activities
                assertTrue(keys.none { it?.name == "invite additional reviewer" })
                assertTrue(keys.none { it?.name == "get review X" })
                assertTrue(keys.none { it?.name == "time-out X" })
                assertEquals(0.0, get(null)!!.min)
                assertEquals(0.0, get(null)!!.median)
                assertEquals(11.0, get(null)!!.max)
                assertEquals(242, get(null)!!.raw.size)

                // instant activities
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "get review 1" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "get review 2" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "get review 3" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "time-out 1" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "time-out 2" }.value.max)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "time-out 3" }.value.max)

                // longer activities [times in days]
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "invite reviewers" }.value.min)
                assertEquals(3.0, entries.first { (k, _) -> k?.name == "invite reviewers" }.value.median)
                assertEquals(12.0, entries.first { (k, _) -> k?.name == "invite reviewers" }.value.max)
                assertEquals(101, entries.first { (k, _) -> k?.name == "invite reviewers" }.value.raw.size)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "decide" }.value.min)
                assertEquals(3.0, entries.first { (k, _) -> k?.name == "decide" }.value.median)
                assertEquals(12.0, entries.first { (k, _) -> k?.name == "decide" }.value.max)
                assertEquals(100, entries.first { (k, _) -> k?.name == "decide" }.value.raw.size)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "accept" }.value.min)
                assertEquals(1.0, entries.first { (k, _) -> k?.name == "accept" }.value.median)
                assertEquals(12.0, entries.first { (k, _) -> k?.name == "accept" }.value.max)
                assertEquals(45, entries.first { (k, _) -> k?.name == "accept" }.value.raw.size)
                assertEquals(0.0, entries.first { (k, _) -> k?.name == "reject" }.value.min)
                assertEquals(4.0, entries.first { (k, _) -> k?.name == "reject" }.value.median)
                assertEquals(9.0, entries.first { (k, _) -> k?.name == "reject" }.value.max)
                assertEquals(55, entries.first { (k, _) -> k?.name == "reject" }.value.raw.size)
            }
            assertNotNull(component.dataLastModified)
            assertNull(component.lastError)
        }
    }

    @Test
    fun `create invalid KPI`() {
        val alignerKpiService = AlignerKPIService()
        try {
            alignerKpiService.register()
            alignerKpiService.start()
            assertEquals(ServiceStatus.Started, alignerKpiService.status)

            createComponent("just invalid query", mainstreamCNetId)

            wctObserver.waitForMessage()
        } finally {
            alignerKpiService.stop()
        }

        transactionMain {
            val component = WorkspaceComponent.find {
                (WorkspaceComponents.name eq "test-aligner-kpi") and (WorkspaceComponents.dataStoreId eq dataStore)
            }.first()

            assertTrue(ProcessModelComponentData.create(component).alignmentKPIReports.isEmpty())
            assertNull(component.dataLastModified)
            assertNotNull(component.lastError)
            assertTrue("Line 1 position 0: mismatched input 'just'" in component.lastError!!, component.lastError)
        }
    }

    @Test
    fun `create invalid KPI then fix it`() {
        val alignerKpiService = AlignerKPIService()
        try {
            alignerKpiService.register()
            alignerKpiService.start()
            assertEquals(ServiceStatus.Started, alignerKpiService.status)

            createComponent("just invalid query", mainstreamCNetId)

            wctObserver.waitForMessage()

            transactionMain {
                val component = WorkspaceComponent.find {
                    (WorkspaceComponents.name eq "test-aligner-kpi") and (WorkspaceComponents.dataStoreId eq dataStore)
                }.first()
                component.query = "select count(^t:name) where l:id=$logUUID"
                component.afterCommit {
                    this as WorkspaceComponent
                    // simulate that the user accepted a new model
                    triggerEvent(
                        Producer(),
                        event = WorkspaceComponentEventType.ModelAccepted
                    )
                }
            }

            wctObserver.waitForMessage()
        } finally {
            alignerKpiService.stop()
        }

        transactionMain {
            val component = WorkspaceComponent.find {
                (WorkspaceComponents.name eq "test-aligner-kpi") and (WorkspaceComponents.dataStoreId eq dataStore)
            }.first()

            val report =
                persistenceProvider.get<Report>(ProcessModelComponentData.create(component).alignmentKPIReports.values.single().values.single())
            assertEquals(2, report.logKPI.size)
            assertEquals(5, report.traceKPI.size)
            assertEquals(1, report.eventKPI.size)

            val kpi = report.logKPI["count(^trace:concept:name)"]!!
            assertEquals(101.0, kpi.min)
            assertEquals(101.0, kpi.max)
            assertEquals(1, kpi.raw.size)
            assertNotNull(component.dataLastModified)
            assertNull(component.lastError)
        }
    }

    @Test
    fun `delete existing KPI report on component removal`() {
        val alignerKpiService = AlignerKPIService()
        try {
            alignerKpiService.register()
            alignerKpiService.start()
            assertEquals(ServiceStatus.Started, alignerKpiService.status)

            val componentId = createComponent(
                "select l:*, t:*, e:name, max(e:timestamp)-min(e:timestamp) where l:id=$logUUID group by e:name, e:instance",
                mainstreamCNetId
            )

            wctObserver.waitForMessage()

            transactionMain {
                WorkspaceComponent.findById(componentId)!!.apply {
                    deleted = true
                    triggerEvent(Producer(), WorkspaceComponentEventType.Delete)
                }
            }

            transactionMain {
                val data = ProcessModelComponentData.create(WorkspaceComponent.findById(componentId)!!).alignmentKPIReports
                assertEquals(1, data.size)
                val componentData = data.values.single().values.single()
                assertNotEquals(URI(""), componentData)

                for (attempt in 1..10) {
                    val result = runCatching {
                        persistenceProvider.get<Report>(componentData)
                    }
                    if (result.isFailure) {
                        assertIs<IllegalArgumentException>(result.exceptionOrNull())
                        break
                    }

                    Thread.sleep(50L)
                }
            }

        } finally {
            alignerKpiService.stop()
        }
    }

    @Test
    fun `changes within the queue delay are aggregated`() {
        System.setProperty(AlignerKPIService.QUEUE_DELAY_MS_PROPERTY, "1000")
        val alignerKpiService = AlignerKPIService()
        val componentId: UUID
        try {
            alignerKpiService.register()
            alignerKpiService.start()
            assertEquals(ServiceStatus.Started, alignerKpiService.status)

            val streamLogId = UUID.randomUUID()
            DBXESOutputStream(DBCache.get(DBTestHelper.dbName).getConnection()).use { output ->
                output.write(sequenceOf(Log(mutableAttributeMapOf(IDENTITY_ID to streamLogId))))
                output.write(
                    DBXESInputStream(
                        DBTestHelper.dbName,
                        Query("where l:id=$logUUID limit t:1")
                    ).filter { it !is Log })
            }

            componentId = createComponent(
                "select l:*, t:*, e:name, max(e:timestamp)-min(e:timestamp) where l:id=$streamLogId group by e:name, e:instance",
                perfectCNetId
            )

            wctObserver.waitForMessage()

            transactionMain {
                val component = WorkspaceComponent.find {
                    WorkspaceComponents.dataStoreId eq dataStore
                }.first()

                val reports = ProcessModelComponentData.create(component).alignmentKPIReports
                assertEquals(1, reports.size)
                assertEquals(1, reports.values.first().size)
            }

            repeat(5) { ctr ->
                AppendingDBXESOutputStream(DBCache.get(DBTestHelper.dbName).getConnection()).use { output ->
                    output.write(sequenceOf(Log(mutableAttributeMapOf(IDENTITY_ID to streamLogId))))
                    output.write(
                        DBXESInputStream(
                            DBTestHelper.dbName,
                            Query("where l:id=$logUUID limit t:1 offset t:${ctr + 1}")
                        ).filter { it !is Log })
                }
                transactionMain {
                    WorkspaceComponent[componentId].triggerEvent(
                        Producer(),
                        event = WorkspaceComponentEventType.NewExternalData
                    )
                }

                Thread.sleep(100L)
            }

            wctObserver.waitForMessage()

        } finally {
            alignerKpiService.stop()
        }

        transactionMain {
            val component = WorkspaceComponent[componentId]

            val reports = ProcessModelComponentData.create(component).alignmentKPIReports
            assertEquals(1, reports.size)
            assertEquals(2, reports.values.first().size)
            val report = persistenceProvider.get<Report>(reports.values.first().maxBy { it.key }.value)
            assertEquals(6, report.alignments.size)
        }
    }


    @Test
    fun `slower changes are not aggregated`() {
        System.setProperty(AlignerKPIService.QUEUE_DELAY_MS_PROPERTY, "100")
        val alignerKpiService = AlignerKPIService()
        val componentId: UUID
        try {
            alignerKpiService.register()
            alignerKpiService.start()
            assertEquals(ServiceStatus.Started, alignerKpiService.status)

            val streamLogId = UUID.randomUUID()
            DBXESOutputStream(DBCache.get(DBTestHelper.dbName).getConnection()).use { output ->
                output.write(sequenceOf(Log(mutableAttributeMapOf(IDENTITY_ID to streamLogId))))
                output.write(
                    DBXESInputStream(
                        DBTestHelper.dbName,
                        Query("where l:id=$logUUID limit t:1")
                    ).filter { it !is Log })
            }

            componentId = createComponent(
                "select l:*, t:*, e:name, max(e:timestamp)-min(e:timestamp) where l:id=$streamLogId group by e:name, e:instance",
                perfectCNetId
            )

            wctObserver.waitForMessage()

            transactionMain {
                val component = WorkspaceComponent.find {
                    WorkspaceComponents.dataStoreId eq dataStore
                }.first()

                val reports = ProcessModelComponentData.create(component).alignmentKPIReports
                assertEquals(1, reports.size)
                assertEquals(1, reports.values.first().size)
            }

            repeat(2) { ctr ->
                AppendingDBXESOutputStream(DBCache.get(DBTestHelper.dbName).getConnection()).use { output ->
                    output.write(sequenceOf(Log(mutableAttributeMapOf(IDENTITY_ID to streamLogId))))
                    output.write(
                        DBXESInputStream(
                            DBTestHelper.dbName,
                            Query("where l:id=$logUUID limit t:1 offset t:${ctr + 1}")
                        ).filter { it !is Log })
                }
                transactionMain {
                    WorkspaceComponent[componentId].triggerEvent(
                        Producer(),
                        event = WorkspaceComponentEventType.NewExternalData
                    )
                }

                Thread.sleep(200L)
            }

            repeat(2) {
                wctObserver.waitForMessage()
            }

        } finally {
            alignerKpiService.stop()
        }

        transactionMain {
            val component = WorkspaceComponent[componentId]

            val reports = ProcessModelComponentData.create(component).alignmentKPIReports
            assertEquals(1, reports.size)
            assertEquals(3, reports.values.first().size)
            for ((idx, entry) in reports.values.first().entries.sortedBy { it.key }.withIndex()) {
                val report = persistenceProvider.get<Report>(entry.value)
                assertEquals(idx + 1, report.alignments.size)
            }
        }
    }
}
