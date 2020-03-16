package processm.services.api

import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.routing.post
import io.ktor.routing.route
import processm.services.api.models.AuthenticationResult
import processm.services.api.models.AuthenticationResultResponse
import processm.services.api.models.ErrorResponse
import processm.services.api.models.UserCredentials
import java.time.Instant
import java.util.*

@KtorExperimentalLocationsAPI
fun Route.UsersApi() {
    val tokenTtl: Long = 60 * 60 * 12
    val jwtIssuer = application.environment.config.property("ktor.jwt.issuer").getString()
    val jwtSecret = application.environment.config.propertyOrNull("ktor.jwt.secret")?.getString()
        ?: JwtAuthentication.generateSecretKey()

    route("/users/session") {
        post {
            val credentials = call.receive<UserCredentials>()

            if (credentials?.password != "pass") {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid username or password"))
            } else {
                val token = JwtAuthentication.createToken(
                    credentials.username,
                    Date.from(Instant.now().plusSeconds(tokenTtl)),
                    jwtIssuer,
                    jwtSecret
                )

                call.respond(HttpStatusCode.Created, AuthenticationResultResponse(AuthenticationResult(token)))
            }
        }
    }

    route("/users") {
        post {
            call.respond(HttpStatusCode.NotImplemented)
        }
    }

    authenticate {
        get<Paths.getUserAccountDetails> { _: Paths.getUserAccountDetails ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        get<Paths.getUsers> { _: Paths.getUsers ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        delete<Paths.signUserOut> { _: Paths.signUserOut ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }
    }
}
