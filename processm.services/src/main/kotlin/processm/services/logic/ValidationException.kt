package processm.services.logic

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Represents result of failed validation in business logic.
 *
 * @property reason the reason of failed validation.
 * @property userMessage message explaining details of failed validation.
 * @property message message passed to parent [Exception]. If not provided, [userMessage] is used.
 */
class ValidationException(val reason: Reason, val userMessage: String, message: String? = null) :
    Exception(message ?: userMessage)

/**
 * Returns [this] or throws [ValidationException] unless `this == true`.
 */
@OptIn(ExperimentalContracts::class)
inline fun Boolean.validate(
    reason: Reason = Reason.ResourceFormatInvalid,
    lazyMessage: () -> Any
): Boolean {
    contract {
        callsInPlace(lazyMessage, InvocationKind.AT_MOST_ONCE)
        returns(true)
    }

    return this || throw ValidationException(reason, lazyMessage().toString())
}

/**
 * Returns [this] or throws [ValidationException] if `this != true`.
 */
inline fun Boolean.validate(
    reason: Reason = Reason.ResourceFormatInvalid,
    message: String = reason.message
): Boolean = validate(reason) { message }

/**
 * Returns [this] or throws [ValidationException] if `this != other`.
 */
inline fun <T : Any> T?.validate(
    other: T?,
    reason: Reason = Reason.ResourceFormatInvalid,
    lazyMessage: () -> Any
): T? {
    (this == other).validate(reason, lazyMessage)
    return this
}

/**
 * Returns [this] or throws [ValidationException] if `this != other`.
 */
inline fun <T : Any> T?.validate(
    other: T?,
    reason: Reason = Reason.ResourceFormatInvalid,
    message: String = reason.message
): T? =
    validate(other, reason) { message }

inline fun Boolean.validateNot(
    reason: Reason = Reason.ResourceFormatInvalid,
    lazyMessage: () -> Any
) = validate(false, reason, lazyMessage)

inline fun <T : Any> T?.validateNot(
    other: T?,
    reason: Reason = Reason.ResourceFormatInvalid,
    lazyMessage: () -> Any
): T? {
    (this != other).validate(reason, lazyMessage().toString())
    return this
}

inline fun <T : Any> T?.validateNot(
    other: T?,
    reason: Reason = Reason.ResourceFormatInvalid,
    message: String = reason.message
): T? =
    validateNot(other, reason) { message }

/**
 * Returns [this] or throws [ValidationException] if `this === null`.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T : Any> T?.validateNotNull(
    reason: Reason = Reason.ResourceFormatInvalid,
    lazyMessage: () -> Any
): T {
    contract {
        callsInPlace(lazyMessage, InvocationKind.AT_MOST_ONCE)
        returns() implies (this@validateNotNull != null)
    }

    if (this !== null)
        return this

    throw ValidationException(reason, lazyMessage().toString())
}

/**
 * Returns [this] or throws [ValidationException] if `this === null`.
 */
inline fun <T : Any> T?.validateNotNull(
    reason: Reason = Reason.ResourceFormatInvalid,
    message: String = reason.message
): T = validateNotNull(reason) { message }

/**
 * Returns [this] or throws [ValidationException] if [this] does not match the [pattern].
 */
@OptIn(ExperimentalContracts::class)
inline fun <T : CharSequence> T?.validatePattern(
    pattern: Regex,
    reason: Reason = Reason.ResourceFormatInvalid,
    lazyMessage: () -> Any
): T {
    contract {
        callsInPlace(lazyMessage, InvocationKind.AT_MOST_ONCE)
        returns() implies (this@validatePattern !== null)
    }

    if (pattern.matches(this.validateNotNull(reason, lazyMessage)))
        return this

    throw ValidationException(reason, lazyMessage().toString())
}

/**
 * Returns [this] or throws [ValidationException] if [this] does not match the [pattern].
 */
inline fun <T : CharSequence> T?.validatePattern(
    pattern: Regex,
    reason: Reason = Reason.ResourceFormatInvalid,
    message: String = reason.message
): T = this.validatePattern(pattern, reason) { message }

/**
 * Throws [ValidationException] if `this !== null`.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T : Any> T?.validateNull(
    reason: Reason = Reason.ResourceAlreadyExists,
    lazyMessage: () -> Any
) {
    contract {
        callsInPlace(lazyMessage, InvocationKind.AT_MOST_ONCE)
        returns() implies (this@validateNull == null)
    }

    if (this === null)
        return

    throw ValidationException(reason, lazyMessage().toString())
}