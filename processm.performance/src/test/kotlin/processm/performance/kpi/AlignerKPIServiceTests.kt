package processm.performance.kpi

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import processm.core.esb.Artemis
import processm.core.esb.ServiceStatus
import processm.core.log.DBXESOutputStream
import processm.core.log.InferConceptInstanceFromStandardLifecycle
import processm.core.log.XMLXESInputStream
import processm.core.log.attribute.IDAttr
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import processm.miners.triggerEvent
import java.util.*
import kotlin.test.*

class AlignerKPIServiceTests {
    companion object {

        val dataStore = UUID.randomUUID()
        val logUUID = UUID.randomUUID()
        val artemis = Artemis()
        var perfectCNetId: Long = -1L
        var mainstreamCNetId: Long = -1L

        @JvmStatic
        @BeforeAll
        fun setUp() {
            DBXESOutputStream::class.java.getResourceAsStream("/xes-logs/JournalReview-extra.xes").use { stream ->
                DBXESOutputStream(DBCache.get(dataStore.toString()).getConnection()).use { output ->
                    output.write(InferConceptInstanceFromStandardLifecycle(XMLXESInputStream(stream)).map {
                        if (it is processm.core.log.Log)
                            processm.core.log.Log(
                                HashMap(it.attributes).apply { put("identity:id", IDAttr("identity:id", logUUID)) },
                                HashMap(it.extensions),
                                HashMap(it.traceGlobals),
                                HashMap(it.eventGlobals),
                                HashMap(it.traceClassifiers),
                                HashMap(it.eventClassifiers)
                            )
                        else
                            it
                    })
                }
            }

            perfectCNetId = createPerfectCNet()
            mainstreamCNetId = createMainstreamCNet()

            artemis.register()
            artemis.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            artemis.stop()
            DBSerializer.delete(DBCache.get(dataStore.toString()).database, perfectCNetId.toInt())
            DBSerializer.delete(DBCache.get(dataStore.toString()).database, mainstreamCNetId.toInt())
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

    fun createKPIComponent(_query: String, _modelId: Long) {
        transaction(DBCache.getMainDBPool().database) {
            WorkspaceComponent.new {
                name = "test-aligner-kpi"
                componentType = ComponentTypeDto.AlignerKpi
                dataStoreId = dataStore
                query = _query
                modelType = ModelTypeDto.CausalNet
                modelId = _modelId
                workspace = Workspace.all().firstOrNull() ?: Workspace.new { name = "test-workspace" }
            }
        }.triggerEvent()
    }

    @AfterTest
    fun deleteKPIComponent() {
        transaction(DBCache.getMainDBPool().database) {
            WorkspaceComponents.deleteWhere {
                (WorkspaceComponents.name eq "test-aligner-kpi") and (WorkspaceComponents.dataStoreId eq dataStore)
            }
        }
    }

    @Test
    fun `create KPI component based on the perfect model then run service`() {
        createKPIComponent(
            "select l:*, t:*, e:name, max(e:timestamp)-min(e:timestamp) where l:id=$logUUID group by e:name, e:instance",
            perfectCNetId
        )
        val alignerKpiService = AlignerKPIService()
        try {
            alignerKpiService.register()
            alignerKpiService.start()
            assertEquals(ServiceStatus.Started, alignerKpiService.status)

            Thread.sleep(1000L) // wait for calculation
        } finally {
            alignerKpiService.stop()
        }

        transaction(DBCache.getMainDBPool().database) {
            val component = WorkspaceComponent.find {
                WorkspaceComponents.dataStoreId eq dataStore
            }.first()

            val report = Report.fromJson(component.data!!)
            assertEquals(0, report.logKPI.size)
            assertEquals(1, report.traceKPI.size)
            assertEquals(20.0, report.traceKPI["cost:total"]!!.median)
            assertEquals(50, report.traceKPI["cost:total"]!!.raw.size)

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
                assertEquals(399, entries.first { (k, _) -> k?.name == "invite additional reviewer" }.value.raw.size)
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

            createKPIComponent(
                "select l:*, t:*, e:name, max(e:timestamp)-min(e:timestamp) where l:id=$logUUID group by e:name, e:instance",
                mainstreamCNetId
            )

            Thread.sleep(1000L) // wait for calculation
        } finally {
            alignerKpiService.stop()
        }

        transaction(DBCache.getMainDBPool().database) {
            val component = WorkspaceComponent.find {
                WorkspaceComponents.dataStoreId eq dataStore
            }.first()

            val report = Report.fromJson(component.data!!)
            assertEquals(0, report.logKPI.size)
            assertEquals(1, report.traceKPI.size)
            assertEquals(20.0, report.traceKPI["cost:total"]!!.median)
            assertEquals(50, report.traceKPI["cost:total"]!!.raw.size)

            with(report.eventKPI.getRow("max(event:time:timestamp) - min(event:time:timestamp)")) {
                // missing activities
                assertTrue(keys.none { it?.name == "invite additional reviewer" })
                assertTrue(keys.none { it?.name == "get review X" })
                assertTrue(keys.none { it?.name == "time-out X" })
                assertEquals(0.0, get(null)!!.min)
                assertEquals(0.0, get(null)!!.median)
                assertEquals(11.0, get(null)!!.max)
                assertEquals(798, get(null)!!.raw.size)

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
        createKPIComponent("just invalid query", mainstreamCNetId)
        val alignerKpiService = AlignerKPIService()
        try {
            alignerKpiService.register()
            alignerKpiService.start()
            assertEquals(ServiceStatus.Started, alignerKpiService.status)

            Thread.sleep(1000L) // wait for calculation
        } finally {
            alignerKpiService.stop()
        }

        transaction(DBCache.getMainDBPool().database) {
            val component = WorkspaceComponent.find {
                WorkspaceComponents.dataStoreId eq dataStore
            }.first()

            assertNull(component.data)
            assertNull(component.dataLastModified)
            assertNotNull(component.lastError)
            assertTrue("Line 1 position 0: mismatched input 'just'" in component.lastError!!, component.lastError)
        }
    }

    @Test
    fun `create invalid KPI then fix it`() {
        createKPIComponent("just invalid query", mainstreamCNetId)
        val alignerKpiService = AlignerKPIService()
        try {
            alignerKpiService.register()
            alignerKpiService.start()
            assertEquals(ServiceStatus.Started, alignerKpiService.status)

            Thread.sleep(1000L) // wait for calculation

            transaction(DBCache.getMainDBPool().database) {
                val component = WorkspaceComponent.find {
                    (WorkspaceComponents.name eq "test-aligner-kpi") and (WorkspaceComponents.dataStoreId eq dataStore)
                }.first()
                component.query = "select count(^t:name) where l:id=$logUUID"
                component
            }.triggerEvent()


            Thread.sleep(1000L) // wait for calculation
        } finally {
            alignerKpiService.stop()
        }

        transaction(DBCache.getMainDBPool().database) {
            val component = WorkspaceComponent.find {
                WorkspaceComponents.dataStoreId eq dataStore
            }.first()

            val report = Report.fromJson(component.data!!)
            assertEquals(1, report.logKPI.size)
            assertEquals(0, report.traceKPI.size)
            assertEquals(0, report.eventKPI.size)

            val kpi = report.logKPI["count(^trace:concept:name)"]!!
            assertEquals(101.0, kpi.min)
            assertEquals(101.0, kpi.max)
            assertEquals(1, kpi.raw.size)
            assertNotNull(component.dataLastModified)
            assertNull(component.lastError)
        }
    }
}