package processm.enhancement.kpi

import processm.core.DBTestHelper
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.petrinet.petrinet
import processm.core.querylanguage.Query
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
    fun `time per activity on the mainstream model`() {
        val log = q(
            "select t:*, e:name, e:instance, sum(e:total), max(e:timestamp)-min(e:timestamp) where l:id=$logUUID group by e:name, e:instance"
        )
        for (model in listOf(mainstreamPetriNet, mainstreamCNet)) {
            val calculator = Calculator(model)
            val report = calculator.calculate(log)

            val traceCostTotal = report.traceKPI["cost:total"]!!
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
        }
    }

    @Test
    fun `time per activity on the perfect model`() {
        val log = q(
            "select t:*, e:name, e:instance, sum(e:total), max(e:timestamp)-min(e:timestamp) where l:id=$logUUID group by e:name, e:instance"
        )
        for (model in listOf(perfectPetriNet, perfectCNet)) {
            val calculator = Calculator(model)
            val report = calculator.calculate(log)

            val traceCostTotal = report.traceKPI["cost:total"]!!
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
        }
    }

    private fun q(pql: String): Log =
        DBHierarchicalXESInputStream(dbName, Query(pql), false).first()
}
