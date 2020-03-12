package processm.core.verifiers

import processm.core.models.processtree.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessTreeVerifierTest {
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
        val model = processTree { Activity("A") }

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
                Exclusive(
                    Activity("A"),
                    Activity("B")
                ),
                RedoLoop(
                    SilentActivity(),
                    Activity("C"),
                    Activity("D")
                ),
                Activity("K")
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
        val seq = Sequence(
            Activity("A"),
            Activity("B")
        )

        val model = processTree {
            Exclusive(
                seq,
                Activity("C")
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
    fun `Invalid tree - child used twice`() {
        val a = Activity("A")
        val model = processTree {
            Sequence(
                a,
                Exclusive(
                    a,
                    Activity("B")
                )
            )
        }

        with(ProcessTreeVerifier().verify(model) as ProcessTreeVerificationReport) {
            assertFalse(isTree)

            assertTrue(isSafe)
            assertTrue(hasOptionToComplete)
            assertTrue(hasProperCompletion)
            assertTrue(noDeadParts)
        }
    }
}