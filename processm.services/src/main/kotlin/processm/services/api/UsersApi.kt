package processm.services.api

import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.parseAuthorizationHeader
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.request.authorization
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.routing.post
import io.ktor.routing.route
import processm.services.api.models.*
import java.time.Duration
import java.time.Instant


@KtorExperimentalLocationsAPI
fun Route.UsersApi() {
    val jwtIssuer = application.environment.config.property("ktor.jwt.issuer").getString()
    val jwtSecret = application.environment.config.propertyOrNull("ktor.jwt.secret")?.getString()
        ?: JwtAuthentication.generateSecretKey()
    val jwtTokenTtl = Duration.parse(
        application.environment.config.property("ktor.jwt.tokenTtl").getString())

    route("/users/session") {
        post {
            val credentials = call.receiveOrNull<UserCredentialsMessageBody>()

            if (credentials != null) {
                if (credentials?.data.password != "pass") {
                    call.respond(HttpStatusCode.Unauthorized, ErrorMessageBody("Invalid username or password"))
                } else {
                    val token = JwtAuthentication.createToken(
                        credentials.data.username,
                        Instant.now().plus(jwtTokenTtl),
                        jwtIssuer,
                        jwtSecret
                    )

                    call.respond(HttpStatusCode.Created, AuthenticationResultMessageBody(AuthenticationResult(token)))
                }
            }
            else if (call.request.authorization() != null) {
                val authorizationHeader = call.request.parseAuthorizationHeader()

                if (authorizationHeader is HttpAuthHeader.Single) {

                    val prolongedToken = JwtAuthentication.verifyAndProlongToken(
                        authorizationHeader.blob,
                        jwtIssuer,
                        jwtSecret,
                        jwtTokenTtl)
                    call.respond(HttpStatusCode.Created, AuthenticationResultMessageBody(AuthenticationResult(prolongedToken)))
                }
            }
            else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorMessageBody("Either user credentials or authentication token needs to be provided"))
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

            call.respond(HttpStatusCode.OK, UserAccountInfoMessageBody(UserAccountInfo(
                principal!!.userId,
            organizationRoles = mapOf("org1" to OrganizationRole.owner))))
        }

        get<Paths.getUsers> { _: Paths.getUsers ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.OK, UserInfoCollectionMessageBody(emptyArray()))
        }

        delete<Paths.signUserOut> { _: Paths.signUserOut ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
