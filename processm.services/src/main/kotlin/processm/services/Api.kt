package processm.services

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import org.koin.dsl.module
import org.koin.ktor.ext.Koin
import processm.core.communication.Producer
import processm.core.logging.loggedScope
import processm.services.api.*
import processm.services.logic.*
import java.time.LocalDateTime

@OptIn(ExperimentalStdlibApi::class)
@KtorExperimentalLocationsAPI
fun Application.apiModule() {
    loggedScope { logger ->
        logger.info("Starting API module")
        install(DefaultHeaders)
        install(ContentNegotiation) {
            // TODO: replace with kotlinx/serialization
            gson(ContentType.Application.Json) {
                // Correctly serialize/deserialize LocalDateTime
                registerTypeAdapter(LocalDateTime::class.java, object : TypeAdapter<LocalDateTime>() {
                    override fun write(out: JsonWriter, value: LocalDateTime?) {
                        out.value(value?.toString())
                    }

                    override fun read(`in`: JsonReader): LocalDateTime = LocalDateTime.parse(`in`.nextString())
                })
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
