package processm.services.logic

class ValidationException(val reason: Reason, val userMessage: String, message: String? = null) : Exception(message ?: userMessage) {

    enum class Reason {
        ResourceAlreadyExists,
        ResourceNotFound
    }
}

