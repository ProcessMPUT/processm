package processm.core.verifiers

/**
 * Base model verification report
 *
 * In standard form model is sound if and only if:
 * - is safe
 * - has option to complete
 * - has proper completion
 * - no dead parts in model
 */
open class VerificationReport(
    val isSafe: Boolean,
    val hasOptionToComplete: Boolean,
    val hasProperCompletion: Boolean,
    val noDeadParts: Boolean
) {
    open val isSound: Boolean = isSafe && hasOptionToComplete && hasProperCompletion && noDeadParts
}
