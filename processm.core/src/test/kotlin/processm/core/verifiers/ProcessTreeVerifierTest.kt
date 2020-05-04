package processm.core.verifiers

import processm.core.models.processtree.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessTreeVerifierTest {
    private val A = ProcessTreeActivity("A")
    private val B = ProcessTreeActivity("B")
    private val C = ProcessTreeActivity("C")
    private val D = ProcessTreeActivity("D")
    private val E = ProcessTreeActivity("E")

    @Test
    fun `Model without nodes is correct process tree`() {
        val model = processTree { null }

        with(ProcessTreeVerifier().verify(model) as ProcessTreeVerificationReport) {
            assertTrue(isTree)

            assertTrue(isSafe)
            assertTrue(hasOptionToComplete)
            assertTrue(hasProperCompletion)
            assertTrue(noDeadParts)
        }
    }

    @Test
    fun `Model with single activity is correct process tree`() {
        val model = processTree { A }

        with(ProcessTreeVerifier().verify(model) as ProcessTreeVerificationReport) {
            assertTrue(isTree)

            assertTrue(isSafe)
            assertTrue(hasOptionToComplete)
            assertTrue(hasProperCompletion)
            assertTrue(noDeadParts)
        }
    }

    @Test
    fun `Correct process tree - in response isTree is set`() {
        val model = processTree {
            Sequence(
                Exclusive(A, B),
                RedoLoop(SilentActivity(), C, D),
                E
            )
        }

        with(ProcessTreeVerifier().verify(model) as ProcessTreeVerificationReport) {
            assertTrue(isTree)

            assertTrue(isSafe)
            assertTrue(hasOptionToComplete)
            assertTrue(hasProperCompletion)
            assertTrue(noDeadParts)
        }
    }

    @Test
    fun `Correct tree built in two steps`() {
        val seq = Sequence(A, B)
        val model = processTree { Exclusive(seq, C) }

        with(ProcessTreeVerifier().verify(model) as ProcessTreeVerificationReport) {
            assertTrue(isTree)

            assertTrue(isSafe)
            assertTrue(hasOptionToComplete)
            assertTrue(hasProperCompletion)
            assertTrue(noDeadParts)
        }
    }

    @Test
    fun `Invalid tree - child used twice`() {
        val model = processTree { Sequence(A, Exclusive(A, B)) }

        with(ProcessTreeVerifier().verify(model) as ProcessTreeVerificationReport) {
            assertFalse(isTree)

            assertTrue(isSafe)
            assertTrue(hasOptionToComplete)
            assertTrue(hasProperCompletion)
            assertTrue(noDeadParts)
        }
    }
}