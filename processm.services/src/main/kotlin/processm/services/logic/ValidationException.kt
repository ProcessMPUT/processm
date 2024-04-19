package processm.services.logic

import processm.services.helpers.ExceptionReason
import processm.services.helpers.LocalizedException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Represents result of failed validation in business logic.
 *
 * @see LocalizedException
 */
class ValidationException(
    reason: ExceptionReason,
    arguments: Array<out Any?> = emptyArray(),
    message: String? = null
) : LocalizedException(reason, arguments, message)

/**
 * Returns [this] or throws [ValidationException] unless `this == true`.
 */
@OptIn(ExperimentalContracts::class)
inline fun Boolean.validate(
    reason: ExceptionReason,
    vararg arguments: Any?
): Boolean {
    contract {
        returns(true)
    }

    return this || throw ValidationException(reason, arguments)
}

/**
 * Returns [this] or throws [ValidationException] if `this != other`.
 */
inline fun <T : Any> T?.validate(
    other: T?,
    reason: ExceptionReason,
    vararg arguments: Any?
): T? {
    (this == other).validate(reason, *arguments)
    return this
}

inline fun Boolean.validateNot(
    reason: ExceptionReason,
    vararg arguments: Any?
) = validate(false, reason, arguments)

inline fun <T : Any> T?.validateNot(
    other: T?,
    reason: ExceptionReason,
    vararg arguments: Any?
): T? {
    (this != other).validate(reason, *arguments)
    return this
}

/**
 * Returns [this] or throws [ValidationException] if `this === null`.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T : Any> T?.validateNotNull(
    reason: ExceptionReason,
    vararg arguments: Any?
): T {
    contract {
        returns() implies (this@validateNotNull != null)
    }

    if (this !== null)
        return this

    throw ValidationException(reason, arguments)
}

/**
 * Throws [ValidationException] if `this !== null`.
 */
inline fun <T : Any> T?.validateNull(
    reason: ExceptionReason,
    vararg arguments: Any?
) {
    if (this === null)
        return

    throw ValidationException(reason, arguments)
}