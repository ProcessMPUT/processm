package processm.enhancement.kpi

import processm.conformance.measures.precision.causalnet.times
import processm.core.DBTestHelper
import processm.core.log.Helpers.assertDoubleEquals
import processm.core.log.Helpers.event
import processm.core.log.Helpers.trace
import processm.core.log.attribute.Attribute.COST_TOTAL
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.BasicMetadata.COUNT
import processm.core.models.metadata.BasicMetadata.LEAD_TIME
import processm.core.models.metadata.BasicMetadata.SERVICE_TIME
import processm.core.models.metadata.BasicMetadata.SUSPENSION_TIME
import processm.core.models.metadata.BasicMetadata.WAITING_TIME
import processm.core.models.petrinet.petrinet
import processm.core.models.processtree.ProcessTrees
import processm.core.querylanguage.Query
import processm.helpers.totalDays
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalculatorTests {
    companion object {
        private val dbName = DBTestHelper.dbName
        private val logUUID: UUID = DBTestHelper.JournalReviewExtra
        private val perfectPetriNet = petrinet {
            P tout "invite reviewers"
            P tin "invite reviewers" tout "get review 1" * "time-out 1"
            P tin "invite reviewers" tout "get review 2" * "time-out 2"
            P tin "invite reviewers" tout "get review 3" * "time-out 3"
            P tin "get review 1" * "time-out 1" tout "collect reviews"
            P tin "get review 2" * "time-out 2" tout "collect reviews"
            P tin "get review 3" * "time-out 3" tout "collect reviews"
            P tin "collect reviews" tout "decide"
            P tin "decide" * "get review X" * "time-out X" tout "accept" * "reject" * "invite additional reviewer"
            P tin "invite additional reviewer" tout "get review X" * "time-out X"
            P tin "accept" * "reject"
        }
        private val mainstreamPetriNet = petrinet {
            P tout "invite reviewers"
            P tin "invite reviewers" tout "get review 1" * "time-out 1"
            P tin "invite reviewers" tout "get review 2" * "time-out 2"
            P tin "invite reviewers" tout "get review 3" * "time-out 3"
            P tin "get review 1" * "time-out 1" tout "collect reviews"
            P tin "get review 2" * "time-out 2" tout "collect reviews"
            P tin "get review 3" * "time-out 3" tout "collect reviews"
            P tin "collect reviews" tout "decide"
            P tin "decide" tout "accept" * "reject"
            P tin "accept" * "reject"
        }

        private val perfectCNet by lazy {
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
            causalnet {
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
        }

        private val mainstreamCNet by lazy {
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
            causalnet {
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
        }
    }

    @Test
    fun `time per activity on the mainstream model - CNet`() =
        `time per activity on the mainstream model`(mainstreamCNet)

    @Test
    fun `time per activity on the mainstream model - PetriNet`() =
        `time per activity on the mainstream model`(mainstreamPetriNet)

    @Test
    fun `time per activity on the mainstream model - ProcessTree`() =
        `time per activity on the mainstream model`(ProcessTrees.journalReviewMainstreamProcessTree)

    private fun `time per activity on the mainstream model`(model: ProcessModel) {
        val log = q(
            "select t:*, e:name, e:instance, sum(e:total), max(e:timestamp)-min(e:timestamp) where l:id=$logUUID group by e:name, e:instance"
        )
        val calculator = Calculator(model)
        val report = calculator.calculate(log)

        val traceCostTotal = report.traceKPI[COST_TOTAL]!!
        println("trace cost:total: $traceCostTotal")
        assertEquals(50, traceCostTotal.raw.size)
        assertEquals(11.0, traceCostTotal.min)
        assertEquals(20.0, traceCostTotal.median)
        assertEquals(47.0, traceCostTotal.max)

        val eventServiceTime = report.eventKPI.getRow("max(event:time:timestamp) - min(event:time:timestamp)")
        assertEquals(11 + 1 /*null*/, eventServiceTime.size)
        println("Service times for activities:")
        for ((activity, kpi) in eventServiceTime) {
            println("$activity: $kpi")
        }

        with(model.activities) {
            // missing activities
            assertTrue(eventServiceTime.keys.none { it?.name == "invite additional reviewer" })
            assertTrue(eventServiceTime.keys.none { it?.name == "get review X" })
            assertTrue(eventServiceTime.keys.none { it?.name == "time-out X" })
            assertEquals(0.0, eventServiceTime[null]!!.min)
            assertEquals(0.0, eventServiceTime[null]!!.median)
            assertEquals(11.0, eventServiceTime[null]!!.max)
            assertEquals(798, eventServiceTime[null]!!.raw.size)

            // instant activities
            assertEquals(0.0, eventServiceTime[first { it.name == "get review 1" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "get review 2" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "get review 3" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "time-out 1" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "time-out 2" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "time-out 3" }]!!.max)

            // longer activities [times in days]
            assertEquals(0.0, eventServiceTime[first { it.name == "invite reviewers" }]!!.min)
            assertEquals(3.0, eventServiceTime[first { it.name == "invite reviewers" }]!!.median)
            assertEquals(12.0, eventServiceTime[first { it.name == "invite reviewers" }]!!.max)
            assertEquals(101, eventServiceTime[first { it.name == "invite reviewers" }]!!.raw.size)
            assertEquals(0.0, eventServiceTime[first { it.name == "decide" }]!!.min)
            assertEquals(3.0, eventServiceTime[first { it.name == "decide" }]!!.median)
            assertEquals(12.0, eventServiceTime[first { it.name == "decide" }]!!.max)
            assertEquals(100, eventServiceTime[first { it.name == "decide" }]!!.raw.size)
            assertEquals(0.0, eventServiceTime[first { it.name == "accept" }]!!.min)
            assertEquals(1.0, eventServiceTime[first { it.name == "accept" }]!!.median)
            assertEquals(12.0, eventServiceTime[first { it.name == "accept" }]!!.max)
            assertEquals(45, eventServiceTime[first { it.name == "accept" }]!!.raw.size)
            assertEquals(0.0, eventServiceTime[first { it.name == "reject" }]!!.min)
            assertEquals(4.0, eventServiceTime[first { it.name == "reject" }]!!.median)
            assertEquals(9.0, eventServiceTime[first { it.name == "reject" }]!!.max)
            assertEquals(55, eventServiceTime[first { it.name == "reject" }]!!.raw.size)
        }

        with(report.arcKPI.getRow(COUNT.urn)) {
            with(entries.single { it.key.source.name == "decide" && it.key.target.name == "accept" }.value) {
                assertEquals(45.0, min)
                assertEquals(45.0, median)
                assertEquals(45.0, max)
                assertEquals(1, count)
            }
            with(entries.single { it.key.source.name == "decide" && it.key.target.name == "reject" }.value) {
                assertEquals(55.0, min)
                assertEquals(55.0, median)
                assertEquals(55.0, max)
                assertEquals(1, count)
            }
        }
    }

    @Test
    fun `time per activity on the perfect model - CNet`() =
        `time per activity on the perfect model`(perfectCNet)

    @Test
    fun `time per activity on the perfect model - PetriNet`() =
        `time per activity on the perfect model`(perfectPetriNet)

    @Test
    fun `time per activity on the perfect model - ProcessTree`() =
        `time per activity on the perfect model`(ProcessTrees.journalReviewPerfectProcessTree)

    private fun `time per activity on the perfect model`(model: ProcessModel) {
        val log = q(
            "select t:*, e:name, e:instance, sum(e:total), max(e:timestamp)-min(e:timestamp) where l:id=$logUUID group by e:name, e:instance"
        )
        val calculator = Calculator(model)
        val report = calculator.calculate(log)

        val traceCostTotal = report.traceKPI[COST_TOTAL]!!
        println("trace cost:total: $traceCostTotal")
        assertEquals(50, traceCostTotal.raw.size)
        assertEquals(11.0, traceCostTotal.min)
        assertEquals(20.0, traceCostTotal.median)
        assertEquals(47.0, traceCostTotal.max)

        val eventServiceTime = report.eventKPI.getRow("max(event:time:timestamp) - min(event:time:timestamp)")
        assertEquals(14, eventServiceTime.size)
        println("Service times for activities:")
        for ((activity, kpi) in eventServiceTime) {
            println("$activity: $kpi")
        }

        with(model.activities) {
            // instant activities
            assertEquals(0.0, eventServiceTime[first { it.name == "get review 1" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "get review 2" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "get review 3" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "get review X" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "time-out 1" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "time-out 2" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "time-out 3" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "time-out X" }]!!.max)

            // longer activities [times in days]
            assertEquals(0.0, eventServiceTime[first { it.name == "invite reviewers" }]!!.min)
            assertEquals(3.0, eventServiceTime[first { it.name == "invite reviewers" }]!!.median)
            assertEquals(12.0, eventServiceTime[first { it.name == "invite reviewers" }]!!.max)
            assertEquals(101, eventServiceTime[first { it.name == "invite reviewers" }]!!.raw.size)
            assertEquals(0.0, eventServiceTime[first { it.name == "decide" }]!!.min)
            assertEquals(3.0, eventServiceTime[first { it.name == "decide" }]!!.median)
            assertEquals(12.0, eventServiceTime[first { it.name == "decide" }]!!.max)
            assertEquals(100, eventServiceTime[first { it.name == "decide" }]!!.raw.size)
            assertEquals(0.0, eventServiceTime[first { it.name == "invite additional reviewer" }]!!.min)
            assertEquals(2.0, eventServiceTime[first { it.name == "invite additional reviewer" }]!!.median)
            assertEquals(11.0, eventServiceTime[first { it.name == "invite additional reviewer" }]!!.max)
            assertEquals(399, eventServiceTime[first { it.name == "invite additional reviewer" }]!!.raw.size)
            assertEquals(0.0, eventServiceTime[first { it.name == "accept" }]!!.min)
            assertEquals(1.0, eventServiceTime[first { it.name == "accept" }]!!.median)
            assertEquals(12.0, eventServiceTime[first { it.name == "accept" }]!!.max)
            assertEquals(45, eventServiceTime[first { it.name == "accept" }]!!.raw.size)
            assertEquals(0.0, eventServiceTime[first { it.name == "reject" }]!!.min)
            assertEquals(4.0, eventServiceTime[first { it.name == "reject" }]!!.median)
            assertEquals(9.0, eventServiceTime[first { it.name == "reject" }]!!.max)
            assertEquals(55, eventServiceTime[first { it.name == "reject" }]!!.raw.size)
        }

        with(report.arcKPI.getRow(COUNT.urn)) {
            with(entries.single { it.key.source.name == "invite additional reviewer" && it.key.target.name == "time-out X" }.value) {
                assertEquals(198.0, min)
                assertEquals(198.0, average)
                assertEquals(198.0, max)
                assertEquals(1, count)
            }
            with(entries.single { it.key.source.name == "invite additional reviewer" && it.key.target.name == "get review X" }.value) {
                assertEquals(201.0, min)
                assertEquals(201.0, average)
                assertEquals(201.0, max)
                assertEquals(1, count)
            }
        }
    }

    private fun q(pql: String): Log =
        DBHierarchicalXESInputStream(dbName, Query(pql), false).first()


    @Test
    fun `two parallel tasks in a process tree`() {
        val log = Log(
            traces =
            trace(event("a", "time" to 1L), event("b", "time" to 2L), event("c", "time" to 10L)).times(10) +
                    trace(event("b", "time" to 3L), event("a", "time" to 2L), event("c", "time" to 20L)).times(10)
        )
        val report = Calculator(ProcessTrees.twoParallelTasksAndSingleFollower).calculate(log)
        with(report.arcKPI.getRow(COUNT.urn).entries) {
            with(single { it.key.source.name == "a" && it.key.target.name == "c" }.value) {
                assertEquals(20.0, average)
            }
            with(single { it.key.source.name == "b" && it.key.target.name == "c" }.value) {
                assertEquals(20.0, average)
            }
        }
    }

    @Test
    fun `loop with a repeated activity`() {
        val log = Log(
            traces =
            sequenceOf(
                trace(
                    event("a", "time:timestamp" to Instant.ofEpochSecond(1L)),
                    event("c", "time:timestamp" to Instant.ofEpochSecond(10L)),
                    event("b", "time:timestamp" to Instant.ofEpochSecond(20L)),
                    event("a", "time:timestamp" to Instant.ofEpochSecond(100L))
                )
            )
        )
        val report = Calculator(ProcessTrees.loopWithRepeatedActivityInRedo).calculate(log)
        fun getWaitingTime(a: String, b: String): Double =
            report.arcKPI.getRow(WAITING_TIME.urn).entries.single { it.key.source.name == a && it.key.target.name == b }.value.median

        with(report.arcKPI) {
            assertEquals(Duration.ofSeconds(9L).totalDays, getWaitingTime("a", "c"))
            assertEquals(Duration.ofSeconds(19L).totalDays, getWaitingTime("a", "b"))
            assertEquals(Duration.ofSeconds(90L).totalDays, getWaitingTime("c", "a"))
            assertEquals(Duration.ofSeconds(80L).totalDays, getWaitingTime("b", "a"))
        }
    }

    @Test
    fun `straightforward loop`() {
        val log = Log(
            traces =
            sequenceOf(
                trace(
                    event("a", "time" to 1L),
                    event("b", "time" to 10L),
                    event("c", "time" to 2L),
                    event("a", "time" to 1L)
                )
            )
        )
        val report = Calculator(ProcessTrees.loopWithSequenceAndExclusiveInRedo).calculate(log)
        assertTrue { report.arcKPI.getRow("time").entries.none { it.key.source.name == "b" && it.key.target.name == "a" } }
    }

    @Test
    fun `inferred lead service waiting suspension times - perfect Cnet`() {
        `inferred lead service waiting suspension times`(perfectCNet)
    }

    @Test
    fun `inferred lead service waiting suspension times - perfect PetriNet`() {
        `inferred lead service waiting suspension times`(perfectCNet)
    }

    @Test
    fun `inferred lead service waiting suspension times - perfect ProcessTree`() {
        `inferred lead service waiting suspension times`(ProcessTrees.journalReviewPerfectProcessTree)
    }

    private fun `inferred lead service waiting suspension times`(model: ProcessModel) {
        val log = q("where l:id=$logUUID")
        val calculator = Calculator(model)
        val report = calculator.calculate(log)

        val eventLeadTime = report.eventKPI.getRow(LEAD_TIME.urn)
        val eventServiceTime = report.eventKPI.getRow(SERVICE_TIME.urn)
        val eventWaitingTime = report.eventKPI.getRow(WAITING_TIME.urn)
        val eventSuspensionTime = report.eventKPI.getRow(SUSPENSION_TIME.urn)
        assertEquals(14, eventLeadTime.size)
        assertEquals(14, eventServiceTime.size)
        assertEquals(14, eventWaitingTime.size)
        assertEquals(14, eventSuspensionTime.size)
        println("Lead times for activities:")
        for ((activity, kpi) in eventLeadTime) {
            println("$activity: $kpi")
        }

        println("Service times for activities:")
        for ((activity, kpi) in eventServiceTime) {
            println("$activity: $kpi")
        }

        println("Waiting times for activities:")
        for ((activity, kpi) in eventWaitingTime) {
            println("$activity: $kpi")
        }

        println("Suspension times for activities:")
        for ((activity, kpi) in eventSuspensionTime) {
            println("$activity: $kpi")
        }

        println("Lead time for arc KPI:")
        for ((arc, kpi) in report.arcKPI.getRow(LEAD_TIME.urn)) {
            println("$arc: $kpi")
        }

        println("Service time for arc KPI:")
        for ((arc, kpi) in report.arcKPI.getRow(SERVICE_TIME.urn)) {
            println("$arc: $kpi")
        }

        println("Waiting time for arc KPI:")
        for ((arc, kpi) in report.arcKPI.getRow(WAITING_TIME.urn)) {
            println("$arc: $kpi")
        }

        println("Suspension time for arc KPI:")
        for ((arc, kpi) in report.arcKPI.getRow(SUSPENSION_TIME.urn)) {
            println("$arc: $kpi")
        }


        with(model.activities) {
            // instant activities
            assertEquals(0.0, eventLeadTime[first { it.name == "get review 1" }]!!.max)
            assertEquals(0.0, eventLeadTime[first { it.name == "get review 2" }]!!.max)
            assertEquals(0.0, eventLeadTime[first { it.name == "get review 3" }]!!.max)
            assertEquals(0.0, eventLeadTime[first { it.name == "get review X" }]!!.max)
            assertEquals(0.0, eventLeadTime[first { it.name == "time-out 1" }]!!.max)
            assertEquals(0.0, eventLeadTime[first { it.name == "time-out 2" }]!!.max)
            assertEquals(0.0, eventLeadTime[first { it.name == "time-out 3" }]!!.max)
            assertEquals(0.0, eventLeadTime[first { it.name == "time-out X" }]!!.max)

            assertEquals(0.0, eventServiceTime[first { it.name == "get review 1" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "get review 2" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "get review 3" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "get review X" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "time-out 1" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "time-out 2" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "time-out 3" }]!!.max)
            assertEquals(0.0, eventServiceTime[first { it.name == "time-out X" }]!!.max)

            assertEquals(0.0, eventWaitingTime[first { it.name == "get review 1" }]!!.max)
            assertEquals(0.0, eventWaitingTime[first { it.name == "get review 2" }]!!.max)
            assertEquals(0.0, eventWaitingTime[first { it.name == "get review 3" }]!!.max)
            assertEquals(0.0, eventWaitingTime[first { it.name == "get review X" }]!!.max)
            assertEquals(0.0, eventWaitingTime[first { it.name == "time-out 1" }]!!.max)
            assertEquals(0.0, eventWaitingTime[first { it.name == "time-out 2" }]!!.max)
            assertEquals(0.0, eventWaitingTime[first { it.name == "time-out 3" }]!!.max)
            assertEquals(0.0, eventWaitingTime[first { it.name == "time-out X" }]!!.max)

            // longer activities [times in days]
            // lead times for activities are wall-clock durations between the first and the last event of an activity instance
            // for JournalReview, where only start and complete events are available, lead times equal service times
            assertEquals(0.0, eventLeadTime[first { it.name == "invite reviewers" }]!!.min)
            assertEquals(3.0, eventLeadTime[first { it.name == "invite reviewers" }]!!.median)
            assertEquals(12.0, eventLeadTime[first { it.name == "invite reviewers" }]!!.max)
            assertEquals(101, eventLeadTime[first { it.name == "invite reviewers" }]!!.raw.size)
            assertEquals(0.0, eventLeadTime[first { it.name == "decide" }]!!.min)
            assertEquals(3.0, eventLeadTime[first { it.name == "decide" }]!!.median)
            assertEquals(12.0, eventLeadTime[first { it.name == "decide" }]!!.max)
            assertEquals(100, eventLeadTime[first { it.name == "decide" }]!!.raw.size)
            assertEquals(0.0, eventLeadTime[first { it.name == "invite additional reviewer" }]!!.min)
            assertEquals(2.0, eventLeadTime[first { it.name == "invite additional reviewer" }]!!.median)
            assertEquals(11.0, eventLeadTime[first { it.name == "invite additional reviewer" }]!!.max)
            assertEquals(399, eventLeadTime[first { it.name == "invite additional reviewer" }]!!.raw.size)
            assertEquals(0.0, eventLeadTime[first { it.name == "accept" }]!!.min)
            assertEquals(1.0, eventLeadTime[first { it.name == "accept" }]!!.median)
            assertEquals(12.0, eventLeadTime[first { it.name == "accept" }]!!.max)
            assertEquals(45, eventLeadTime[first { it.name == "accept" }]!!.raw.size)
            assertEquals(0.0, eventLeadTime[first { it.name == "reject" }]!!.min)
            assertEquals(4.0, eventLeadTime[first { it.name == "reject" }]!!.median)
            assertEquals(9.0, eventLeadTime[first { it.name == "reject" }]!!.max)
            assertEquals(55, eventLeadTime[first { it.name == "reject" }]!!.raw.size)

            assertEquals(0.0, eventServiceTime[first { it.name == "invite reviewers" }]!!.min)
            assertEquals(3.0, eventServiceTime[first { it.name == "invite reviewers" }]!!.median)
            assertEquals(12.0, eventServiceTime[first { it.name == "invite reviewers" }]!!.max)
            assertEquals(101, eventServiceTime[first { it.name == "invite reviewers" }]!!.raw.size)
            assertEquals(0.0, eventServiceTime[first { it.name == "decide" }]!!.min)
            assertEquals(3.0, eventServiceTime[first { it.name == "decide" }]!!.median)
            assertEquals(12.0, eventServiceTime[first { it.name == "decide" }]!!.max)
            assertEquals(100, eventServiceTime[first { it.name == "decide" }]!!.raw.size)
            assertEquals(0.0, eventServiceTime[first { it.name == "invite additional reviewer" }]!!.min)
            assertEquals(2.0, eventServiceTime[first { it.name == "invite additional reviewer" }]!!.median)
            assertEquals(11.0, eventServiceTime[first { it.name == "invite additional reviewer" }]!!.max)
            assertEquals(399, eventServiceTime[first { it.name == "invite additional reviewer" }]!!.raw.size)
            assertEquals(0.0, eventServiceTime[first { it.name == "accept" }]!!.min)
            assertEquals(1.0, eventServiceTime[first { it.name == "accept" }]!!.median)
            assertEquals(12.0, eventServiceTime[first { it.name == "accept" }]!!.max)
            assertEquals(45, eventServiceTime[first { it.name == "accept" }]!!.raw.size)
            assertEquals(0.0, eventServiceTime[first { it.name == "reject" }]!!.min)
            assertEquals(4.0, eventServiceTime[first { it.name == "reject" }]!!.median)
            assertEquals(9.0, eventServiceTime[first { it.name == "reject" }]!!.max)
            assertEquals(55, eventServiceTime[first { it.name == "reject" }]!!.raw.size)
        }

        with(report.arcKPI.getRow(COUNT.urn)) {
            with(entries.single { it.key.source.name == "invite additional reviewer" && it.key.target.name == "time-out X" }.value) {
                assertEquals(198.0, average)
            }
            with(entries.single { it.key.source.name == "invite additional reviewer" && it.key.target.name == "get review X" }.value) {
                assertEquals(201.0, average)
            }
        }

        with(report.arcKPI.getRow(WAITING_TIME.urn)) {
            with(entries.single { it.key.source.name == "invite additional reviewer" && it.key.target.name == "time-out X" }.value) {
                assertEquals(0.0, min)
                assertDoubleEquals(5.025252, average)
                assertEquals(16.0, max)
                assertEquals(198, count)
            }
            with(entries.single { it.key.source.name == "invite additional reviewer" && it.key.target.name == "get review X" }.value) {
                assertEquals(0.0, min)
                assertDoubleEquals(2.60199, average)
                assertEquals(10.0, max)
                assertEquals(201, count)
            }
        }
    }
}
