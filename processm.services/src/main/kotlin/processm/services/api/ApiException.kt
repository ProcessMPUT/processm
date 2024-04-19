package processm.services.api

import io.ktor.http.*
import processm.logging.logger
import java.util.*

/**
 * Represents failure during processing of API request.
 *
 * @property reason the cause of the exception, to be translated and sent to the client, informing about the reason of failed processing.
 * @property arguments the arguments that may be necessary to correctly format the message
 * @property responseCode HTTP status code to be sent with response. 400 Bad Request by default.
 * @property message message passed to parent [Exception]. If not provided, [reason] is used.
 */
class ApiException(
    val reason: ApiExceptionReason,
    val arguments: Array<Any?> = emptyArray(),
    val responseCode: HttpStatusCode = HttpStatusCode.BadRequest,
    message: String? = null
) : Exception(message ?: reason.toString()) {
    //TODO extract reason and arguments as LocalizedException and unify with ValidationException

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

enum class ApiExceptionReason {
    UNSPECIFIED_REASON,
    NO_FIELD_IN_TOKEN,
    NOT_MEMBER_OF_ORGANIZATION,
    INSUFFICIENT_PERMISSION_IN_ORGANIZATION,
    UNPARSABLE_DATA,
    LAST_ACE_CANNOT_BE_DOWNGRADED,
    ENTRY_NOT_FOUND,
    LAST_ACE_CANNOT_BE_REMOVED,
    ACL_CANNOT_BE_MODIFIED,
    ACL_CANNOT_BE_READ,
    INVALID_USERNAME_OR_PASSWORD,

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
    NOT_FOUND,
    TOKEN_EXPIRED,
    CREDENTIALS_OR_TOKEN_ARE_REQUIRED,
    WORKSPACE_NAME_IS_REQUIRED,
    CONNECTION_TEST_FAILED,
    INVALID_TOKEN_FORMAT
}


internal fun Locale.getErrorMessage(key: String): String = ResourceBundle.getBundle("exceptions", this).getString(key)