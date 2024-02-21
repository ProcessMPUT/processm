package processm.services.logic

/**
 * The reason for throwing [ValidationException].
 */
enum class Reason(val message: String) {
    ResourceAlreadyExists("Resource already exists."),
    ResourceNotFound("Resources is not found."),
    ResourceFormatInvalid("Resource format is invalid."),
    UnprocessableResource("Resource is unprocessable."),
    Unauthorized("Unauthorized"),
    Forbidden("Forbidden")
}
