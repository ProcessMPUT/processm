package processm.services

import io.ktor.http.*
import io.ktor.serialization.gson.*
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
import processm.core.logging.loggedScope
import processm.services.api.*
import processm.services.logic.*
import java.time.LocalDateTime

fun Application.apiModule() {
    loggedScope { logger ->
        logger.info("Starting API module")
        install(DefaultHeaders)
        install(ContentNegotiation) {
            // TODO: replace with kotlinx/serialization; this requires the OpenAPI generator to add kotlinx/serialization annotations; currently, this is not supported
            gson(ContentType.Application.Json) {
                // Correctly serialize/deserialize LocalDateTime
                registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                registerTypeAdapterFactory(NonNullableTypeAdapterFactory())
            }
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
                single { WorkspaceService(get(), get()) }
                single { DataStoreService(get()) }
                single { LogsService(get()) }
                single { Producer() }
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
                get { call.respondRedirect("/api-docs/", permanent = true) }
            }
        }
    }
}
