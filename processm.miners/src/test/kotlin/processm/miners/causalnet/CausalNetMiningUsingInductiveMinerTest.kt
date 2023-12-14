package processm.miners.causalnet

import processm.core.DBTestHelper
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.causalnet.converters.toCausalNet
import processm.core.querylanguage.Query
import processm.core.verifiers.CausalNetVerifier
import processm.miners.processtree.inductiveminer.OnlineInductiveMiner
import kotlin.test.Test
import kotlin.test.assertTrue

class CausalNetMiningUsingInductiveMinerTest {
    @Test
    fun journalReview() {
        val log = DBHierarchicalXESInputStream(
            DBTestHelper.dbName,
            Query("where l:id=${DBTestHelper.JournalReviewExtra}")
        )
        val oim = OnlineInductiveMiner()
        val tree = oim.processLog(log)
        val cnet = tree.toCausalNet()
        val verifier = CausalNetVerifier()
        val report = verifier.verify(cnet)

        assertTrue(report.noDeadParts)
        assertTrue(report.hasOptionToComplete)
        assertTrue(report.hasProperCompletion)
        assertTrue(report.isSound)
    }
}
