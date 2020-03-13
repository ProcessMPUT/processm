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

    /**
     * Compute possible extensions for a given valid sequence, according to Definition 3.11 in PM
     */
    val validSequences: SequenceWithMemory<CausalNetSequence> = verifier.validSequences

    /**
     * The set of all valid sequences without loops. This set should never be infinite.
     *
     * Recall that a state is a multi-set of pending obligations.
     * A loop in a sequence occurs if there is a state B such that there is a state A earlier in the sequence, such that
     * both states contains exactly the same pending obligations ignoring the number of occurences (e.g., this is the case for {a} and {a,a})
     * and B contains no less of each obligations than A.
     * In simple terms, B is A plus something.
     */
    val validLoopFreeSequences: SequenceWithMemory<CausalNetSequence> = verifier.validLoopFreeSequences
}