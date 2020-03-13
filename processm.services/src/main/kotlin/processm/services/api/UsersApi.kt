package processm.services.api

import com.google.gson.Gson
import com.typesafe.config.ConfigFactory
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.config.HoconApplicationConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
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
    val gson = Gson()
    val empty = mutableMapOf<String, Any?>()
    val config = HoconApplicationConfig(ConfigFactory.load())
    val jwtIssuer = config.property("ktor.jwt.issuer").getString()
    val jwtSecret = config.propertyOrNull("ktor.jwt.secret")?.getString() ?: JwtAuthentication.generateSecretKey()

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
                val exampleContentType = "*/*"
                val exampleContentString = """{
              "language" : "language",
              "username" : "username",
              "organizationRoles" : { }
            }"""

                when (exampleContentType) {
                    "application/json" -> call.respond(gson.fromJson(exampleContentString, empty::class.java))
                    "application/xml" -> call.respondText(exampleContentString, ContentType.Text.Xml)
                    else -> call.respondText(exampleContentString)
                }
            }
        }

        get<Paths.getUsers> { _: Paths.getUsers ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val exampleContentType = "*/*"
                val exampleContentString = """{
      "organization" : "organization",
      "id" : "id",
      "username" : "username",
      "organizationRoles" : { }
    }"""

                when (exampleContentType) {
                    "application/json" -> call.respond(gson.fromJson(exampleContentString, empty::class.java))
                    "application/xml" -> call.respondText(exampleContentString, ContentType.Text.Xml)
                    else -> call.respondText(exampleContentString)
                }
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
