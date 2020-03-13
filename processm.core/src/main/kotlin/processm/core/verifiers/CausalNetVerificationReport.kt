package processm.core.verifiers

import processm.core.helpers.SequenceWithMemory
import processm.core.verifiers.causalnet.CausalNetSequence
import processm.core.verifiers.causalnet.CausalNetVerifierImpl

/**
 * A verification report extended with all valid sequences and all valid loop-free sequences
 */
class CausalNetVerificationReport internal constructor(verifier: CausalNetVerifierImpl) :
    VerificationReport(
        verifier.isSafe,
        verifier.hasOptionToComplete,
        verifier.hasProperCompletion,
        !verifier.hasDeadParts
    ) {

    val validSequences: SequenceWithMemory<CausalNetSequence> = verifier.validSequences

    val validLoopFreeSequences: SequenceWithMemory<CausalNetSequence> = verifier.validLoopFreeSequences
}