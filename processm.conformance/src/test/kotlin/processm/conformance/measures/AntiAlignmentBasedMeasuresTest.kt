package processm.conformance.measures

import org.junit.jupiter.api.Timeout
import processm.conformance.models.alignments.AStar
import processm.conformance.models.antialignments.TwoPhaseDFS
import processm.core.DBTestHelper
import processm.core.log.DBXESInputStream
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.querylanguage.Query
import java.util.concurrent.TimeUnit
import kotlin.test.Ignore
import kotlin.test.Test

class AntiAlignmentBasedMeasuresTest {

    companion object {

        fun createPerfectCNet(): CausalNet {
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

            return cnet
        }
    }

    @Ignore("Not a test, just a showcase for the performance problem")
    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun `antialignment length = 10 tailored aligner`() {
        val cnet = createPerfectCNet()
        val log = DBXESInputStream(
            DBTestHelper.dbName,
            Query("where l:id=${DBTestHelper.JournalReviewExtra} and transition='complete' limit t:1")
        ).first { it is Log } as Log
        with(TwoPhaseDFS(cnet)) {
            this.align(log, 10)
        }
    }

    @Ignore("Not a test, just a showcase for the performance problem")
    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun `antialignment length = 10 Astar`() {
        val cnet = createPerfectCNet()
        val log = DBXESInputStream(
            DBTestHelper.dbName,
            Query("where l:id=${DBTestHelper.JournalReviewExtra} and transition='complete' limit t:1")
        ).first { it is Log } as Log
        with(TwoPhaseDFS(cnet, alignerFactory = { m, p, _ -> AStar(m, p) })) {
            this.align(log, 10)
        }
    }
}