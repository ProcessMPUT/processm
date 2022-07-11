package processm.services.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * API for retrieving system's config. Only the predefined properties are available through this API.
 * DO NOT extend for retrieving any property, as it would be security vulnerability.
 */
@KtorExperimentalLocationsAPI
fun Route.ConfigApi() {
    get<Paths.Config> { _ ->
        call.respond(HttpStatusCode.OK, Config())
    }
}
