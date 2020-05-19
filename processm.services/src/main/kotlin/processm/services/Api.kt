package processm.services

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.features.*
import io.ktor.gson.GsonConverter
import io.ktor.http.ContentType
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import org.koin.dsl.module
import org.koin.ktor.ext.Koin
import org.koin.ktor.ext.get
import processm.services.api.*
import processm.services.logic.AccountService
import processm.services.logic.GroupService
import processm.services.logic.OrganizationService

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
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
    install(DataConversion, ApplicationDataConversionConfiguration())
    install(Koin) {
        modules(module {
            single { AccountService(get()) }
            single { OrganizationService() }
            single { GroupService() }
        })
    }

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
