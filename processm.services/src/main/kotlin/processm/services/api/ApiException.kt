package processm.services.api

import io.ktor.http.*
import processm.services.helpers.ExceptionReason
import processm.services.helpers.LocalizedException

/**
 * Represents failure during processing of API request.
 *
 * @property reason the cause of the exception, to be translated and sent to the client, informing about the reason of failed processing.
 * @property arguments the arguments that may be necessary to correctly format the message
 * @property responseCode HTTP status code to be sent with response. 400 Bad Request by default.
 * @property message message passed to parent [Exception]. If not provided, [reason] is used.
 */
class ApiException(
    reason: ExceptionReason,
    arguments: Array<Any?> = emptyArray(),
    val responseCode: HttpStatusCode = HttpStatusCode.BadRequest,
    message: String? = null
) : LocalizedException(reason, arguments, message)

