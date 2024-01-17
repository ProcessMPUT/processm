package processm.core.verifiers

import processm.core.models.commons.ProcessModel

/**
 * Verifies properties of a any model.
 */
interface Verifier<T : ProcessModel> {
    fun verify(model: T): VerificationReport
}