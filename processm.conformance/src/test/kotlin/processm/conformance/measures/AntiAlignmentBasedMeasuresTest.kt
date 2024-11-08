package processm.conformance.measures

import org.junit.jupiter.api.Timeout
import processm.conformance.models.alignments.AStar
import processm.conformance.models.antialignments.TwoPhaseDFS
import processm.core.DBTestHelper
import processm.core.log.DBXESInputStream
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.CausalNets
import processm.core.querylanguage.Query
import java.util.concurrent.TimeUnit
import kotlin.test.Ignore
import kotlin.test.Test

class AntiAlignmentBasedMeasuresTest {

    @Ignore("Not a test, just a showcase for the performance problem")
    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun `antialignment length = 10 tailored aligner`() {
        val cnet = CausalNets.perfectJournal
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
        val cnet = CausalNets.perfectJournal
        val log = DBXESInputStream(
            DBTestHelper.dbName,
            Query("where l:id=${DBTestHelper.JournalReviewExtra} and transition='complete' limit t:1")
        ).first { it is Log } as Log
        with(TwoPhaseDFS(cnet, alignerFactory = { m, p, _ -> AStar(m, p) })) {
            this.align(log, 10)
        }
    }
}