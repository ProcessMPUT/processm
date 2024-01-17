package processm.core.verifiers

/**
 * Process tree model verification report
 *
 * Model is sound if and only if:
 * - is a tree, not a graph
 * - is safe
 * - has option to complete
 * - has proper completion
 * - no dead parts in model
 */
class ProcessTreeVerificationReport(
    isSafe: Boolean,
    hasOptionToComplete: Boolean,
    hasProperCompletion: Boolean,
    noDeadParts: Boolean,
    val isTree: Boolean
) : VerificationReport(
    isSafe = isSafe,
    hasOptionToComplete = hasOptionToComplete,
    hasProperCompletion = hasProperCompletion,
    noDeadParts = noDeadParts
) {
    override val isSound: Boolean = isTree && isSafe && hasOptionToComplete && hasProperCompletion && noDeadParts
}