package processm.core.verifiers

import processm.core.models.commons.AbstractModel

/**
 * Verifies properties of a any model.
 */
interface Verifier<T : AbstractModel> {
    fun verify(model: T): VerificationReport
}