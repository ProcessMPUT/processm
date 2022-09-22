package processm.services

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.response.*

/**
 * Responds with the 201 code and Location header set to [url]. Optional [message] is written into response body.
 */
public suspend inline fun <reified T : Any> ApplicationCall.respondCreated(url: String, message: T?) {
    response.header("Location", url)
    if (message !== null)
        respond(HttpStatusCode.Created, message)
    else
        respond(HttpStatusCode.Created)
}

/**
 * Responds with the 201 code and Location header set to [url].
 */
public suspend inline fun ApplicationCall.respondCreated(url: String) = respondCreated(url, null)

/**
 * Responds with the 201 code and Location header set to [path]. Optional [message] is written into response body.
 * @param path An object implementing Locations API path. See [processm.services.api.Paths].
 */
@OptIn(KtorExperimentalLocationsAPI::class)
public suspend inline fun <reified T : Any> ApplicationCall.respondCreated(path: Any, message: T?) {
    val url = application.locations.href(path)
    respondCreated(url, message)
}

/**
 * Responds with the 201 code and Location header set to [path].
 * @param path An object implementing Locations API path. See [processm.services.api.Paths].
 */
@OptIn(KtorExperimentalLocationsAPI::class)
public suspend inline fun ApplicationCall.respondCreated(path: Any) = respondCreated(path, null)

