package processm.services.api

import io.ktor.http.HttpStatusCode

class ApiException(val publicMessage: String?, val responseCode: HttpStatusCode = HttpStatusCode.BadRequest,  message: String? = null)
    : Exception(message ?: publicMessage)

