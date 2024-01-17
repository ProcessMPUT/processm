package processm.core.verifiers

import processm.core.models.causalnet.CausalNet
import processm.core.verifiers.causalnet.CausalNetVerifierImpl

/**
 * Verifies properties of a causal net model.
 *
 * Potentially computationally expensive.
 */
class CausalNetVerifier : Verifier<CausalNet> {
    override fun verify(model: CausalNet): CausalNetVerificationReport {
        return CausalNetVerificationReport(CausalNetVerifierImpl(model))
    }
}