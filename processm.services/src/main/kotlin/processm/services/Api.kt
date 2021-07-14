package processm.services

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import org.koin.dsl.module
import org.koin.ktor.ext.Koin
import processm.core.logging.loggedScope
import processm.services.api.*
import processm.services.logic.*

@OptIn(ExperimentalStdlibApi::class)
@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
fun Application.apiModule() {
    loggedScope { logger ->
        logger.info("Starting API module")
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
                single { WorkspaceService(get()) }
                single { DataSourceService() }
                single { LogsService() }
            })
        }

        routing {
            route("api") {
                GroupsApi()
                OrganizationsApi()
                UsersApi()
                WorkspacesApi()
                DataSourcesApi()
                get { call.respondRedirect("/api-docs/", permanent = true) }
            }
        }
    }
}
