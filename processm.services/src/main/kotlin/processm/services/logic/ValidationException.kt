package processm.services.logic

/**
 * Represents result of failed validation in business logic.
 *
 * @property reason the reason of failed validation.
 * @property userMessage message explaining details of failed validation.
 * @property message message passed to parent [Exception]. If not provided, [userMessage] is used.
 */
class ValidationException(val reason: Reason, val userMessage: String, message: String? = null) : Exception(message ?: userMessage) {

    enum class Reason {
        ResourceAlreadyExists,
        ResourceNotFound,
        ResourceFormatInvalid
    }
}

