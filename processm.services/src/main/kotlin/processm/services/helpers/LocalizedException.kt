package processm.services.helpers

import io.ktor.http.*
import processm.logging.logger
import java.util.*

open class LocalizedException(
    val reason: ExceptionReason,
    val arguments: Array<out Any?> = emptyArray(),
    message: String? = null
) : Exception(message ?: reason.toString()) {

    fun localizedMessage(locale: Locale): String = try {
        val formatString = try {
            locale.getErrorMessage(reason.toString())
        } catch (e: MissingResourceException) {
            logger().warn("Missing translation of {} to {}", reason.toString(), locale)
            Locale.US.getErrorMessage(reason.toString())
        }
        String.format(locale, formatString, *arguments)
    } catch (e: Exception) {
        logger().error("An exception was thrown while preparing localized exception", e)
        message ?: reason.toString()
    }
}

enum class ExceptionReason(val statusCode: HttpStatusCode = HttpStatusCode.BadRequest) {
    UNSPECIFIED_REASON,
    NO_FIELD_IN_TOKEN,
    NOT_MEMBER_OF_ORGANIZATION(HttpStatusCode.Forbidden),
    INSUFFICIENT_PERMISSION_IN_ORGANIZATION(HttpStatusCode.Forbidden),
    UNPARSABLE_DATA,
    LAST_ACE_CANNOT_BE_DOWNGRADED(HttpStatusCode.UnprocessableEntity),
    ENTRY_NOT_FOUND(HttpStatusCode.NotFound),
    LAST_ACE_CANNOT_BE_REMOVED(HttpStatusCode.UnprocessableEntity),
    ACL_CANNOT_BE_MODIFIED(HttpStatusCode.Forbidden),
    ACL_CANNOT_BE_READ(HttpStatusCode.Forbidden),
    INVALID_USERNAME_OR_PASSWORD(HttpStatusCode.Unauthorized),

    @Deprecated("A temporary patch until PQL errors are translated")
    PQL_ERROR,

    UNEXPECTED_REQUEST_PARAMETER,
    NOT_A_VALID_FILE,
    UNSUPPORTED_CONTENT_TYPE,
    CONNECTOR_CONFIGURATION_REQUIRED,
    NAME_FOR_DATA_CONNECTOR_IS_REQUIRED,
    NAME_FOR_DATA_STORE_IS_REQUIRED,
    DATA_CONNECTOR_REFERENCE_IS_REQUIRED,
    NAME_FOR_ETL_PROCESS_IS_REQUIRED,
    EMPTY_ETL_CONFIGURATION_NOT_SUPPORTED,
    ETL_PROCESS_TYPE_NOT_SUPPORTED,
    ACTIVATION_STATUS_IS_REQUIRED,
    NOT_FOUND(HttpStatusCode.NotFound),
    TOKEN_EXPIRED(HttpStatusCode.Unauthorized),
    CREDENTIALS_OR_TOKEN_ARE_REQUIRED,
    WORKSPACE_NAME_IS_REQUIRED,
    CONNECTION_TEST_FAILED,
    INVALID_TOKEN_FORMAT(HttpStatusCode.Unauthorized),
    PARENT_ORGANIZATION_ALREADY_SET,
    INVALID_GROUP,
    INVALID_USER_ID,
    ORGANIZATION_NOT_FOUND(HttpStatusCode.NotFound),
    ORGANIZATION_IS_ALREADY_TOP_LEVEL(HttpStatusCode.UnprocessableEntity),
    ACCOUNT_NOT_FOUND(HttpStatusCode.NotFound),
    GROUP_NOT_FOUND(HttpStatusCode.NotFound),
    USER_NOT_FOUND_IN_ORGANIZATION(HttpStatusCode.NotFound),
    MISSING_DATA_STORE,
    MISSING_COMPONENT_TYPE,
    MISSING_QUERY,
    SHARED_GROUP_NOT_ASSIGNED(HttpStatusCode.NotFound), //TODO review
    WORKSPACE_NOT_FOUND(HttpStatusCode.NotFound),
    WORKSPACE_COMPONENT_NOT_FOUND(HttpStatusCode.NotFound),
    GROUP_IS_SOLE_OWNER(HttpStatusCode.UnprocessableEntity),
    NAME_IS_BLANK,
    INVALID_GROUP_SPECIFICATION,
    CANNOT_DETACH_FROM_SHARED_GROUP,
    CANNOT_DETACH_FROM_IMPLICIT_GROUP,
    USER_OR_GROUP_NOT_FOUND(HttpStatusCode.NotFound),
    ORGANIZATION_NOT_OWN_CHILD,
    ALREADY_DESCENDANT,
    USER_ALREADY_IN_ORGANIZATION(HttpStatusCode.Conflict),
    NOT_A_DIRECT_SUBORGANIZATION,
    CANNOT_CHANGE_ROLE(HttpStatusCode.UnprocessableEntity),
    CANNOT_DELETE(HttpStatusCode.UnprocessableEntity),
    INVALID_LOCALE,
    CANNOT_CHANGE_LOCALE,
    INVALID_EMAIL,
    PASSWORD_TOO_WEAK,
    USER_ALREADY_EXISTS(HttpStatusCode.Conflict),
    INSUFFICIENT_PERMISSION_TO_URN(HttpStatusCode.Forbidden),
    DATA_STORE_NOT_FOUND(HttpStatusCode.NotFound),
    ETL_PROCESS_NOT_FOUND(HttpStatusCode.NotFound)
}


internal fun Locale.getErrorMessage(key: String): String = ResourceBundle.getBundle("exceptions", this).getString(key)