package processm.services

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

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


/**
 * Bind websocket at the location defined by the class [T].
 * Use as you would use the corresponding API for HTTP methods (e.g., [post])
 */
@OptIn(KtorExperimentalLocationsAPI::class)
inline fun <reified T : Any> Route.webSocket(
    noinline handler: suspend DefaultWebSocketServerSession.(T) -> Unit
): Route = location<T> {
    webSocket("") {
        // The following line is copied from Route.handle. In there, it was wrapped in intercept(ApplicationCallPipeline.Plugins) { }
        // It didn't seem to work with webSocket and, at the same time, it does not seem to be necessary, thus I dispensed with it
        val params = call.application.locations.resolve<T>(T::class, call)
        handler(params)
    }
}
