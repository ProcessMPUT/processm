package processm.miners.processtree.inductiveminer

import processm.miners.heuristicminer.Helper.logFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class OnlineInductiveMinerTest {
    @Test
    fun `Discover changed process tree`() {
        val fullMiner = OnlineInductiveMiner()
        fullMiner.discover(
            listOf(
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
        onlineMiner.discover(listOf(firstLog))

        val logWithLastTrace = logFromString(
            """
            A B C F D
            """.trimIndent()
        )
        onlineMiner.discover(listOf(logWithLastTrace))

        val fullMinerTree = fullMiner.result
        val onlineMinerTree = onlineMiner.result

        assertTrue(fullMinerTree.languageEqual(onlineMinerTree))
        assertEquals(fullMinerTree.toString(), onlineMinerTree.toString())
    }
}