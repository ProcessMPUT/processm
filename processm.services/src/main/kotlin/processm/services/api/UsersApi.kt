package processm.services.api

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.locations.*
import io.ktor.server.locations.post
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import processm.core.helpers.mapToArray
import processm.core.logging.loggedScope
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.RoleType
import processm.services.api.models.*
import processm.services.logic.*
import java.time.Duration
import java.time.Instant

fun Route.UsersApi() {
    val accountService by inject<AccountService>()
    val organizationService by inject<OrganizationService>()
    val jwtIssuer = application.environment.config.property("ktor.jwt.issuer").getString()
    val jwtSecret = JwtAuthentication.getSecretKey(application.environment.config.config("ktor.jwt"))
    val jwtTokenTtl = Duration.parse(application.environment.config.property("ktor.jwt.tokenTtl").getString())

    post<Paths.UsersSession> {
        loggedScope { logger ->
            val credentials = call.receiveOrNull<UserCredentials>()

            when {
                credentials != null -> {
                    val token = transactionMain {
                        val user = accountService.verifyUsersCredentials(credentials.login, credentials.password)
                            ?: throw ApiException("Invalid username or password", HttpStatusCode.Unauthorized)
                        val userRolesInOrganizations = accountService.getRolesAssignedToUser(user.id)
                            .map { it.organization.id.value to it.role.toApi() }
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

                        token
                    }
                    call.respond(HttpStatusCode.Created, AuthenticationResult(token))
                }

                call.request.authorization() !== null -> {
                    val authorizationHeader =
                        call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single ?: throw ApiException(
                            "Invalid authorization token format", HttpStatusCode.Unauthorized
                        )
                    val prolongedToken = JwtAuthentication.verifyAndProlongToken(
                        authorizationHeader.blob, jwtIssuer, jwtSecret, jwtTokenTtl
                    )

                    logger.debug("A session token ${authorizationHeader.blob} has been successfully prolonged to $prolongedToken")
                    call.respond(HttpStatusCode.Created, AuthenticationResult(prolongedToken))
                }

                else -> throw ApiException("Either user credentials or authentication token needs to be provided")
            }
        }
    }

    post<Paths.Users> {
        loggedScope { logger ->
            val accountInfo = call.receiveOrNull<AccountRegistrationInfo>()
                ?: throw ApiException("The provided account details cannot be parsed")
            val locale = call.request.acceptLanguageItems().getOrNull(0)

            with(accountInfo) {
                (!newOrganization || !organizationName.isNullOrBlank()) || throw ValidationException(
                    Reason.ResourceFormatInvalid,
                    "Organization name must not be empty."
                )

                transactionMain {
                    val user = accountService.create(userEmail, locale?.value, userPassword)
                    if (newOrganization) {
                        val organization = organizationService.create(organizationName!!, true)
                        organizationService.addMember(organization.id.value, user.id.value, RoleType.Owner)
                    }
                }
            }

            logger.info("A new user ${accountInfo.userEmail} is created")
            call.respond(HttpStatusCode.Created)
        }
    }

    authenticate {
        get<Paths.UserAccountDetails> { _ ->
            val principal = call.authentication.principal<ApiUser>()!!
            val userAccount = accountService.getUser(principal.userId)

            call.respond(
                HttpStatusCode.OK,
                UserAccountInfo(
                    id = userAccount.id.value,
                    email = userAccount.email,
                    locale = userAccount.locale
                )
            )
        }

        route("/users/me") {
            route("/password") {
                patch {
                    loggedScope { logger ->
                        val principal = call.authentication.principal<ApiUser>()!!
                        val passwordData = call.receiveOrNull<PasswordChange>()
                            ?: throw ApiException("The provided password data cannot be parsed")

                        if (accountService.changePassword(
                                principal.userId, passwordData.currentPassword, passwordData.newPassword
                            )
                        ) {
                            logger.info("The user ${principal.userId} has successfully changed his password")
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(
                                HttpStatusCode.Forbidden, ErrorMessage("The current password could not be changed")
                            )
                        }
                    }
                }
            }
            route("/locale") {
                patch {
                    val principal = call.authentication.principal<ApiUser>()!!
                    val localeData = call.receiveOrNull<LocaleChange>()
                        ?: throw ApiException("The provided locale data cannot be parsed")

                    accountService.changeLocale(principal.userId, localeData.locale)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        get<Paths.UserOrganizations> { _ ->
            val principal = call.authentication.principal<ApiUser>()!!
            val userOrganizations = transactionMain {
                accountService.getRolesAssignedToUser(principal.userId).mapToArray { it.organization.toApi() }
            }

            call.respond(HttpStatusCode.OK, userOrganizations)
        }

        get<Paths.Users> { user ->
            val principal = call.authentication.principal<ApiUser>()!!
            val email = call.request.queryParameters["email"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10

            val users = accountService.getUsers(principal.userId, email, limit)
            call.respond(
                HttpStatusCode.OK,
                users.mapToArray {
                    UserAccountInfo(
                        id = it.id.value,
                        email = it.email,
                        locale = it.locale
                    )
                }
            )
        }

        delete<Paths.UsersSession> { _ ->
            loggedScope { logger ->
                val principal = call.authentication.principal<ApiUser>()!!

                logger.debug("The user ${principal.userId} has successfully logged out")
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
