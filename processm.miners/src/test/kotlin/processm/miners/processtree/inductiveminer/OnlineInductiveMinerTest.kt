package processm.miners.processtree.inductiveminer

import processm.core.log.Helpers.logFromString
import processm.core.models.processtree.ProcessTree
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

    @Test
    fun `PM book Figure 2-6 | Figure 7-26`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A B D E H
        A D C E G
        A C D E F B D E G
        A D B E H
        A C D E F D C E F C D E H
        A C D E G
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,⟲(→(∧(×(B,C),D),E),F),×(G,H))", output.toString())
    }

    @Test
    fun `PM book Figure 2-7`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A B D E H
        A D B E H
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,∧(B,D),E,H)", output.toString())
    }

    @Test
    fun `PM book Figure 2-7 with moved E activity - prove start activities correctly recognized`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A E B D H
        A E D B H
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,E,∧(B,D),H)", output.toString())
    }

    @Test
    fun `PM book Figure 7-3`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A E
        A B E
        A C E
        A D E
        A D D E
        A D D D E
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,×(B,C,⟲(D,τ),τ),E)", output.toString())
    }

    @Test
    fun `PM book Figure 7-20`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
         A B C D
         A B C D
         A B C D
         A C B D
         A C B D
         A E D
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,×(∧(B,C),E),D)", output.toString())
    }

    @Test
    fun `PM book Figure 7-24`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
         A B C D
         A B C D
         A B C D
         A C B D
         A C B D
         A C B D
         A C B D
         A B C E F B C D
         A B C E F B C D
         A C B E F B C D
         A C B E F B C D
         A B C E F C B D
         A C B E F B C E F C B D
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,⟲(∧(B,C),→(E,F)),D)", output.toString())
    }

    @Test
    fun `PM book Figure 6-5 | 7-29 Q3`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A B C D E F B D C E G
        A B D C E G
        A B D C E G
        A B C D E F B C D E F B D C E G
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,⟲(→(B,∧(C,D),E),F),G)", output.toString())
    }

    @Test
    fun `PM book Figure 6-6 | 7-29 Q4`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A C D
        B C D
        A C E
        B C E
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(×(A,B),C,×(D,E))", output.toString())
    }

    @Test
    fun `PM book Figure 6-8 | 7-29 Q5`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A B E F
        A B E F
        A B E C D B F
        A B E C D B F
        A B E C D B F
        A B C E D B F
        A B C E D B F
        A B C D E B F
        A B C D E B F
        A B C D E B F
        A B C D E B F
        A E B C D B F
        A E B C D B F
        A E B C D B F
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,∧(⟲(B,→(C,D)),E),F)", output.toString())
    }

    @Test
    fun `PM book Figure 7-29 Q6`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A C E G
        A C E G
        A E C G
        A E C G
        A E C G
        B D F G
        B D F G
        B F D G
        B F D G
        B F D G
         """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(×(→(A,∧(C,E)),→(B,∧(D,F))),G)", output.toString())
    }

    @Test
    fun `Based on PM book Figure 7-29 Q6 - extra activities in sequence`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A C E G X Y Z
        A C E G X Y Z
        A E C G X Y Z
        A E C G X Y Z
        A E C G X Y Z
        B D F G X Y Z
        B D F G X Y Z
        B F D G X Y Z
        B F D G X Y Z
        B F D G X Y Z
        B F D G X Y Z
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(×(→(A,∧(C,E)),→(B,∧(D,F))),G,X,Y,Z)", output.toString())
    }

    @Test
    fun `PM book Figure 6-11 | 7-29 Q7`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A C
        A C
        A B C
        A B C
        A B C
        A B B C
        A B B C
        A B B B B C
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,⟲(τ,B),C)", output.toString())
    }

    @Test
    fun `PM book Figure 6-12 | 7-29 Q8`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A B D
        A B D
        A B D
        A B C B D
        A B C B D
        A B C B C B D
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,⟲(B,C),D)", output.toString())
    }

    @Test
    fun `Optional activity as first in loop`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A B D
        A C B D
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,×(C,τ),B,D)", output.toString())
    }

    @Test
    fun `PM book Figure 7-29 Q9`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A C D
        B C E
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(×(A,B),C,×(D,E))", output.toString())
    }

    @Test
    fun `PM book Figure 7-29 Q10`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A A
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("⟲(A,τ)", output.toString())
    }

    @Test
    fun `PM book Figure 6-21 | 7-29 Q11`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A C
        A B C
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,×(B,τ),C)", output.toString())
    }

    @Test
    fun `PM book Figure 7-30 F1`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A B C D
        A C B D
        A E F D
        A E D
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,×(∧(B,C),→(E,×(F,τ))),D)", output.toString())
    }

    @Test
    fun `Exclusive cut should be able to add tau if trace support not sum to parent's support`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A B C D
        A C B D
        A E F D
        A E D
        A D
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,×(∧(B,C),→(E,×(F,τ)),τ),D)", output.toString())
    }

    @Test
    fun `Optional activity in parallel cut`() {
        var output: ProcessTree? = null
        val onlineMiner = OnlineInductiveMiner()
        """
        A B C D
        A C B D
        A C D
        """.trimIndent().split("\n").forEach { rawLog ->
            val log = logFromString(rawLog)
            output = onlineMiner.processLog(sequenceOf(log))
        }

        assertEquals("→(A,∧(×(B,τ),C),D)", output.toString())
    }
}
