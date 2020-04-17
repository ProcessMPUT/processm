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
import io.ktor.request.acceptLanguageItems
import io.ktor.request.authorization
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.*
import org.koin.ktor.ext.inject
import processm.services.api.models.*
import processm.services.logic.AccountService
import java.time.Duration
import java.time.Instant

@KtorExperimentalLocationsAPI
fun Route.UsersApi() {
    val accountService by inject<AccountService>()
    val jwtIssuer = application.environment.config.property("ktor.jwt.issuer").getString()
    val jwtSecret = application.environment.config.propertyOrNull("ktor.jwt.secret")?.getString()
                    ?: JwtAuthentication.generateSecretKey()
    val jwtTokenTtl = Duration.parse(application.environment.config.property("ktor.jwt.tokenTtl").getString())

    route("/users/session") {
        post {
            val credentials = call.receiveOrNull<UserCredentialsMessageBody>()?.data

            when {
                credentials != null -> {
                    var user = accountService.verifyUsersCredentials(credentials.username, credentials.password)
                               ?: throw ApiException("Invalid username or password", HttpStatusCode.Unauthorized)
                    val token = JwtAuthentication.createToken(
                        user.id.value, user.username, Instant.now().plus(jwtTokenTtl), jwtIssuer, jwtSecret)

                    call.respond(
                        HttpStatusCode.Created, AuthenticationResultMessageBody(AuthenticationResult(token)))
                }
                call.request.authorization() != null -> {
                    val authorizationHeader =
                        call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single ?: throw ApiException(
                            "Invalid authorization token format", HttpStatusCode.Unauthorized)
                    val prolongedToken = JwtAuthentication.verifyAndProlongToken(
                        authorizationHeader.blob, jwtIssuer, jwtSecret, jwtTokenTtl)

                    call.respond(
                        HttpStatusCode.Created, AuthenticationResultMessageBody(AuthenticationResult(prolongedToken)))
                }
                else -> {
                    throw ApiException("Either user credentials or authentication token needs to be provided")
                }
            }
        }
    }

    route("/users") {
        post {
            val accountInfo = call.receiveOrNull<AccountRegistrationInfoMessageBody>()?.data
                              ?: throw ApiException("The provided account details cannot be parsed")
            val locale = call.request.acceptLanguageItems().getOrNull(0)

            accountService.createAccount(accountInfo.userEmail, accountInfo.organizationName, locale?.value)
            call.respond(HttpStatusCode.Created)
        }
    }

    authenticate {
        get<Paths.getUserAccountDetails> { _: Paths.getUserAccountDetails ->
            val principal = call.authentication.principal<ApiUser>()!!
            val userAccountDetails = accountService.getAccountDetails(principal.userId)

            call.respond(
                HttpStatusCode.OK, UserAccountInfoMessageBody(
                    UserAccountInfo(
                        userAccountDetails.username, userAccountDetails.locale)))
        }

        route("/users/me") {
            route("/password") {
                patch {
                    val principal = call.authentication.principal<ApiUser>()!!
                    val passwordData = call.receiveOrNull<PasswordChangeMessageBody>()?.data
                                       ?: throw ApiException("The provided password data cannot be parsed")

                    if (accountService.changePassword(
                            principal.userId, passwordData.currentPassword, passwordData.newPassword)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(
                            HttpStatusCode.Forbidden, ErrorMessageBody("The current password could not be changed"))
                    }
                }
            }
            route("/locale") {
                patch {
                    val principal = call.authentication.principal<ApiUser>()!!
                    val localeData = call.receiveOrNull<LocaleChangeMessageBody>()?.data
                                     ?: throw ApiException("The provided locale data cannot be parsed")

                    accountService.changeLocale(principal.userId, localeData.locale)
                    call.respond(HttpStatusCode.OK)
                }
            }
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
