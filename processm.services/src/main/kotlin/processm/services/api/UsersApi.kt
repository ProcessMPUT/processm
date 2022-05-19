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
import processm.core.logging.loggedScope
import processm.services.api.models.*
import processm.services.logic.AccountService
import java.time.Duration
import java.time.Instant

@KtorExperimentalLocationsAPI
fun Route.UsersApi() {
    val accountService by inject<AccountService>()
    val jwtIssuer = application.environment.config.property("ktor.jwt.issuer").getString()
    val jwtSecret = JwtAuthentication.getSecretKey(application.environment.config.config("ktor.jwt"))
    val jwtTokenTtl = Duration.parse(application.environment.config.property("ktor.jwt.tokenTtl").getString())

    route("/users/session") {
        post {
            loggedScope { logger ->
                val credentials = call.receiveOrNull<UserCredentialsMessageBody>()?.data

                when {
                    credentials != null -> {
                        val user = accountService.verifyUsersCredentials(credentials.login, credentials.password)
                            ?: throw ApiException("Invalid username or password", HttpStatusCode.Unauthorized)
                        val userRolesInOrganizations = accountService.getRolesAssignedToUser(user.id)
                            .map { it.organization.id to OrganizationRole.valueOf(it.role.roleName) }
                            .toMap()
                        val token = JwtAuthentication.createToken(
                            user.id,
                            user.email,
                            userRolesInOrganizations,
                            Instant.now().plus(jwtTokenTtl),
                            jwtIssuer,
                            jwtSecret
                        )

                        logger.debug("The user ${user.id} has successfully logged in")
                        call.respond(
                            HttpStatusCode.Created, AuthenticationResultMessageBody(AuthenticationResult(token))
                        )
                    }
                    call.request.authorization() != null -> {
                        val authorizationHeader =
                            call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single ?: throw ApiException(
                                "Invalid authorization token format", HttpStatusCode.Unauthorized
                            )
                        val prolongedToken = JwtAuthentication.verifyAndProlongToken(
                            authorizationHeader.blob, jwtIssuer, jwtSecret, jwtTokenTtl
                        )

                        logger.debug("A session token ${authorizationHeader.blob} has been successfully prolonged to $prolongedToken")
                        call.respond(
                            HttpStatusCode.Created,
                            AuthenticationResultMessageBody(AuthenticationResult(prolongedToken))
                        )
                    }
                    else -> {
                        throw ApiException("Either user credentials or authentication token needs to be provided")
                    }
                }
            }
        }
    }

    route("/users") {
        post {
            loggedScope { logger ->
                val accountInfo = call.receiveOrNull<AccountRegistrationInfoMessageBody>()?.data
                    ?: throw ApiException("The provided account details cannot be parsed")
                val locale = call.request.acceptLanguageItems().getOrNull(0)

                accountService.createAccount(accountInfo.userEmail, accountInfo.organizationName, locale?.value)

                logger.info("A new organization account for ${accountInfo.organizationName} has been successfully created")
                call.respond(HttpStatusCode.Created)
            }
        }
    }

    authenticate {
        get<Paths.UserAccountDetails> { _ ->
            val principal = call.authentication.principal<ApiUser>()!!
            val userAccount = accountService.getAccountDetails(principal.userId)

            call.respond(
                HttpStatusCode.OK, UserAccountInfoMessageBody(
                    UserAccountInfo(
                        userAccount.email, userAccount.locale
                    )
                )
            )
        }

        route("/users/me") {
            route("/password") {
                patch {
                    loggedScope { logger ->
                        val principal = call.authentication.principal<ApiUser>()!!
                        val passwordData = call.receiveOrNull<PasswordChangeMessageBody>()?.data
                            ?: throw ApiException("The provided password data cannot be parsed")

                        if (accountService.changePassword(
                                principal.userId, passwordData.currentPassword, passwordData.newPassword
                            )
                        ) {
                            logger.info("The user ${principal.userId} has successfully changed his password")
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(
                                HttpStatusCode.Forbidden, ErrorMessageBody("The current password could not be changed")
                            )
                        }
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

        get<Paths.UserOrganizations> { _ ->
            val principal = call.authentication.principal<ApiUser>()!!
            val userOrganizations = accountService.getRolesAssignedToUser(principal.userId)
                .map {
                    UserOrganization(
                        it.organization.id,
                        it.organization.name,
                        OrganizationRole.valueOf(it.role.roleName)
                    )
                }
                .toTypedArray()

            call.respond(HttpStatusCode.OK, UserOrganizationCollectionMessageBody(userOrganizations))
        }

        get<Paths.Users> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.OK, UserInfoCollectionMessageBody(emptyArray()))
        }

        delete<Paths.UserOut> { _ ->
            loggedScope { logger ->
                val principal = call.authentication.principal<ApiUser>()!!

                logger.debug("The user ${principal.userId} has successfully logged out")
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
