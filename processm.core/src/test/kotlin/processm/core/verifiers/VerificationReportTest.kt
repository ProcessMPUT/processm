package processm.core.verifiers

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationReportTest {
    @Test
    fun `As default is sound if all fields are true`() {
        with(
            VerificationReport(
                isSafe = true,
                hasOptionToComplete = true,
                hasProperCompletion = true,
                noDeadParts = true
            )
        ) {
            assertTrue(isSound)
        }
    }

    @Test
    fun `If one of fields is false == is not sound`() {
        for (isSafe in setOf(true, false)) {
            for (hasOptionToComplete in setOf(true, false)) {
                for (hasProperCompletion in setOf(true, false)) {
                    for (noDeadParts in setOf(true, false)) {
                        with(
                            VerificationReport(
                                isSafe = isSafe,
                                hasOptionToComplete = hasOptionToComplete,
                                hasProperCompletion = hasProperCompletion,
                                noDeadParts = noDeadParts
                            )
                        ) {
                            if (listOf(isSafe, hasOptionToComplete, hasProperCompletion, noDeadParts).any { !it })
                                assertFalse(isSound)
                            else
                                assertTrue(isSound)
                        }
                    }
                }
            }
        }

    }
}