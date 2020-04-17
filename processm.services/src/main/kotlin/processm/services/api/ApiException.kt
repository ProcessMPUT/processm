package processm.services.api

import io.ktor.http.HttpStatusCode

/**
 * Represents failure during processing of API request.
 *
 * @property publicMessage message to be sent to API client, informing about the reason of failed processing.
 * @property responseCode HTTP status code to be sent with response. 400 Bad Request by default.
 * @property message message passed to parent [Exception]. If not provided, [publicMessage] is used.
 */
class ApiException(val publicMessage: String?, val responseCode: HttpStatusCode = HttpStatusCode.BadRequest,  message: String? = null)
    : Exception(message ?: publicMessage)

