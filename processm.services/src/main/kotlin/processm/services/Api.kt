package processm.services

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.locations.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.dataconversion.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.hsts.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import processm.core.communication.Producer
import processm.logging.loggedScope
import processm.services.api.*
import processm.services.logic.*

fun Application.apiModule() {
    loggedScope { logger ->
        logger.info("Starting API module")
        install(DefaultHeaders)
        install(ContentNegotiation) {
            // Use deserializer for String too:
            removeIgnoredType(String::class)
            json(JsonSerializer, ContentType.Application.Json)
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
                single { AccountService(get(), get()) }
                single { GroupService() }
                single { OrganizationService(get(), get()) }
                single { ACLService() }
                single { WorkspaceService(get(), get(), get()) }
                single { DataStoreService(get(), get(), get()) }
                single { LogsService(get()) }
                single { Producer() }
                single { NotificationService(get()) }
            })
        }

        routing {
            route("api") {
                ConfigApi()
                GroupsApi()
                OrganizationsApi()
                UsersApi()
                WorkspacesApi()
                DataStoresApi()
                ACLApi()
                NotificationsApi()
                get { call.respondRedirect("/api-docs/", permanent = true) }
            }
        }
    }
}
