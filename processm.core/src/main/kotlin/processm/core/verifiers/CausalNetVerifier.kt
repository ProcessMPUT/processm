package processm.core.verifiers

import processm.core.models.causalnet.Model
import processm.core.verifiers.causalnet.CausalNetVerifierImpl

/**
 * Verifies properties of a causal net model.
 *
 * Potentially computationally expensive.
 */
class CausalNetVerifier : Verifier<Model> {
    override fun verify(model: Model): CausalNetVerificationReport {
        return CausalNetVerificationReport(CausalNetVerifierImpl(model))
    }
}