package processm.core.verifiers

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
    val validSequences: Sequence<CausalNetSequence> = verifier.validSequences

    /**
     * The set of all valid sequences without loops. This set should never be infinite.
     *
     * Recall that a state is a multi-set of pending obligations.
     * A loop in a sequence occurs if there is a state B such that there is a state A earlier in the sequence, such that
     * both states contains exactly the same pending obligations ignoring the number of occurences (e.g., this is the case for {a} and {a,a})
     * and B contains no less of each obligations than A.
     * In simple terms, B is A plus something.
     */
    val validLoopFreeSequences: Sequence<CausalNetSequence> = verifier.validLoopFreeSequences

    /**
     * An approximation of the subset of all valid sequences without loops with a single, arbitrary serialization whenever there is paralellism involved.
     *
     * This employs an heuristic approach: partial sequence is discarded whenever we already visited the state reached by this partial sequence using exactly the same set of nodes as this partial sequence.
     * That is, for the causal net `a (b || c) d` (where `||` denotes parallel execution) it will yield either `a c b d` or `a b c d`, but not both, because the second one will be discarded before `d`
     * Conversely, for the causal net `a (b ^ c) d` (where `^` denotes XOR) both `a b d` and `a c d` will be returned
     */
    val validLoopFreeSequencesWithArbitrarySerialization = verifier.validLoopFreeSequencesWithArbitrarySerialization
}
