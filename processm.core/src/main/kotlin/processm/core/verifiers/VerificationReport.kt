package processm.core.verifiers

open class VerificationReport(
    val isSafe: Boolean,
    val hasOptionToComplete: Boolean,
    val hasProperCompletion: Boolean,
    val noDeadParts: Boolean
) {
    val isSound: Boolean = isSafe && hasOptionToComplete && hasProperCompletion && noDeadParts
}
