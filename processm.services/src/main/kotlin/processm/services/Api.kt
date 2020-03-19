package processm.services

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.features.*
import io.ktor.gson.GsonConverter
import io.ktor.http.ContentType
import io.ktor.locations.Locations
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import processm.services.api.*

fun Application.apiModule() {

    install(DefaultHeaders)
    install(ContentNegotiation) {
        register(ContentType.Application.Json, GsonConverter())
    }
    install(AutoHeadResponse)
    install(HSTS, ApplicationHstsConfiguration())
    install(Compression, ApplicationCompressionConfiguration())
    install(Locations)
    install(StatusPages, ApplicationStatusPageConfiguration())
    install(Authentication, ApplicationAuthenticationConfiguration(environment.config.config("ktor.jwt")))

    routing {
        route("api") {
            GroupsApi()
            OrganizationsApi()
            UsersApi()
            WorkspacesApi()
            get { call.respondRedirect("/api-docs/", permanent = true) }
        }
    }
}
