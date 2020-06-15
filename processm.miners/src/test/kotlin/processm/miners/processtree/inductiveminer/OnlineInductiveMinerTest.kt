package processm.miners.processtree.inductiveminer

import processm.miners.heuristicminer.Helper.logFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class OnlineInductiveMinerTest {
    @Test
    fun `Discover changed process tree`() {
        val offlineModel = OfflineInductiveMiner().processLog(
            sequenceOf(
                logFromString(
                    """
                    A B C D
                    A C B D
                    A C D
                    """.trimIndent()
                )
            )
        )

        val onlineMiner = OnlineInductiveMiner()
        val firstLog = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )
        val firstStepModel = onlineMiner.processLog(sequenceOf(firstLog))

        val logWithLastTrace = logFromString(
            """
            A C D
            """.trimIndent()
        )
        val finalOnlineModel = onlineMiner.processLog(sequenceOf(logWithLastTrace))

        assertTrue(offlineModel.languageEqual(finalOnlineModel))
        assertEquals(offlineModel.toString(), finalOnlineModel.toString())

        assertEquals("→(A,∧(B,C),D)", firstStepModel.toString())
        assertEquals("→(A,∧(×(B,τ),C),D)", finalOnlineModel.toString())
    }
}