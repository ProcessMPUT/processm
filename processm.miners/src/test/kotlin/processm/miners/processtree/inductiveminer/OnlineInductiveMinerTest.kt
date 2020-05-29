package processm.miners.processtree.inductiveminer

import processm.miners.heuristicminer.Helper.logFromString
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class OnlineInductiveMinerTest {
    @Ignore("Online without propagation of trace support - can build not correct trees")
    @Test
    fun `Discover changed process tree`() {
        val fullMiner = OnlineInductiveMiner()
        fullMiner.discover(
            sequenceOf(
                logFromString(
                    """
                    A B C D
                    A C B D
                    A E F D
                    A B C F D
                    """.trimIndent()
                )
            )
        )

        val onlineMiner = OnlineInductiveMiner()
        val firstLog = logFromString(
            """
            A B C D
            A C B D
            A E F D
            """.trimIndent()
        )
        onlineMiner.discover(sequenceOf(firstLog))

        val logWithLastTrace = logFromString(
            """
            A B C F D
            """.trimIndent()
        )
        onlineMiner.discover(sequenceOf(logWithLastTrace))

        val fullMinerTree = fullMiner.result
        val onlineMinerTree = onlineMiner.result

        // offline: →(A,×(∧(B,C),E),×(F,τ),D)
        // online: →(A,×(∧(B,C),E),F,D)

        assertTrue(fullMinerTree.languageEqual(onlineMinerTree))
        assertEquals(fullMinerTree.toString(), onlineMinerTree.toString())
    }
}