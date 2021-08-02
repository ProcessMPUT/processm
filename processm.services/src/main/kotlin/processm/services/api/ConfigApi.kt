package processm.services.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*

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
