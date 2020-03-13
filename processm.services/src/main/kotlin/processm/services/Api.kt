package processm.services

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.jwt
import io.ktor.features.*
import io.ktor.gson.GsonConverter
import io.ktor.http.ContentType
import io.ktor.locations.Locations
import io.ktor.routing.route
import io.ktor.routing.routing
import processm.services.api.*
import java.time.Instant

fun Application.apiModule() {
    val jwtIssuer = environment.config.property("ktor.jwt.issuer").getString()
    val jwtRealm = environment.config.property("ktor.jwt.realm").getString()
    val jwtSecret = environment.config.propertyOrNull("ktor.jwt.secret")?.getString() ?: JwtAuthentication.generateSecretKey()

    install(DefaultHeaders)
    install(ContentNegotiation) {
        register(ContentType.Application.Json, GsonConverter())
    }
    install(AutoHeadResponse)
    install(HSTS, ApplicationHstsConfiguration())
    install(Compression, ApplicationCompressionConfiguration())
    install(Locations)
    install(Authentication) {

        jwt {
            realm = jwtRealm
            verifier(JwtAuthentication.verifier(jwtIssuer, jwtSecret))
            validate {
                if (Instant.now().isBefore(it.payload.expiresAt.toInstant())) {
                    val identificationClaim = it.payload.claims["id"]?.asString()

                    if (!identificationClaim.isNullOrEmpty()) ApiUser(identificationClaim) else null
                }
                else null
            }
        }
    }

    routing {
        route("api") {
            GroupsApi()
            OrganizationsApi()
            UsersApi()
            WorkspacesApi()
        }
    }
}
