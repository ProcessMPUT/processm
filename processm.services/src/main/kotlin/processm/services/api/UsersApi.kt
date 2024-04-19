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
import processm.core.persistence.connection.transactionMain
import processm.helpers.mapToArray
import processm.logging.loggedScope
import processm.services.api.models.*
import processm.services.helpers.ExceptionReason
import processm.services.logic.AccountService
import processm.services.logic.OrganizationService
import processm.services.logic.ValidationException
import processm.services.logic.toApi
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
            val credentials = runCatching { call.receiveNullable<UserCredentials>() }.getOrNull()

            when {
                credentials != null -> {
                    val token = transactionMain {
                        val user = accountService.verifyUsersCredentials(credentials.login, credentials.password)
                            ?: throw ApiException(ExceptionReason.INVALID_USERNAME_OR_PASSWORD)
                        val userRolesInOrganizations = accountService.getRolesAssignedToUser(user.id.value)
                            .associate { it.organization.id.value to it.role.toApi() }
                        val token = JwtAuthentication.createToken(
                            user.id.value,
                            user.email,
                            userRolesInOrganizations,
                            Instant.now().plus(jwtTokenTtl),
                            jwtIssuer,
                            jwtSecret
                        )

                        logger.debug("The user {} has successfully logged in", user.id.value)

                        token
                    }
                    call.respond(HttpStatusCode.Created, AuthenticationResult(token))
                }

                call.request.authorization() !== null -> {
                    val authorizationHeader =
                        call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single ?: throw ApiException(
                            ExceptionReason.INVALID_TOKEN_FORMAT
                        )
                    val prolongedToken = JwtAuthentication.verifyAndProlongToken(
                        authorizationHeader.blob, jwtIssuer, jwtSecret, jwtTokenTtl
                    ) { userId,
                        username,
                        _,
                        expiration,
                        issuer,
                        secret ->
                        transactionMain {
                            val userRolesInOrganizations = accountService.getRolesAssignedToUser(userId)
                                .associate { it.organization.id.value to it.role.toApi() }
                            JwtAuthentication.createToken(
                                userId,
                                username,
                                userRolesInOrganizations,
                                expiration,
                                issuer,
                                secret
                            )
                        }
                    }

                    logger.debug("A session token ${authorizationHeader.blob} has been successfully prolonged to $prolongedToken")
                    call.respond(HttpStatusCode.Created, AuthenticationResult(prolongedToken))
                }

                else -> throw ApiException(ExceptionReason.CREDENTIALS_OR_TOKEN_ARE_REQUIRED)
            }
        }
    }

    post<Paths.Users> {
        loggedScope { logger ->
            val accountInfo = runCatching { call.receiveNullable<AccountRegistrationInfo>() }.getOrNull()
                ?: throw ApiException(ExceptionReason.UNPARSABLE_DATA)
            val locale = call.request.acceptLanguageItems().getOrNull(0)

            with(accountInfo) {
                (!newOrganization || !organizationName.isNullOrBlank()) || throw ValidationException(ExceptionReason.NAME_IS_BLANK)

                transactionMain {
                    val user = accountService.create(userEmail, locale?.value, userPassword)
                    if (newOrganization) {
                        val organization = organizationService.create(
                            organizationName!!,
                            true,
                            ownerUserId = user.id.value
                        )
                    }
                }
            }

            logger.info("A new user ${accountInfo.userEmail} is created")
            call.respond(HttpStatusCode.Created)
        }
    }

    post<Paths.ResetPasswordRequest> {
        loggedScope { logger ->
            val request = runCatching { call.receiveNullable<ResetPasswordRequest>() }.getOrNull()
                ?: throw ApiException(ExceptionReason.UNPARSABLE_DATA)
            try {
                accountService.sendPasswordResetEmail(request.email)
            } catch (e: Throwable) {
                // Logged but no information is returned to the frontend to avoid leaking information about registered users
                logger.error("Suppressed error during sending password reset request for `${request.email}`", e)
            }
            call.respond(HttpStatusCode.Accepted)
        }
    }

    post<Paths.ResetPassword> { path ->
        loggedScope {
            val token = path.token
            val request =
                runCatching { call.receiveNullable<PasswordChange>() }.getOrNull()
                    ?: throw ApiException(ExceptionReason.UNPARSABLE_DATA)
            if (accountService.resetPasswordWithToken(token, request.newPassword))
                call.respond(HttpStatusCode.OK)
            else
                call.respond(HttpStatusCode.Forbidden)
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
                        val passwordData = runCatching { call.receiveNullable<PasswordChange>() }.getOrNull()
                            ?: throw ApiException(ExceptionReason.UNPARSABLE_DATA)

                        if (accountService.changePassword(
                                principal.userId, passwordData.currentPassword, passwordData.newPassword
                            )
                        ) {
                            logger.info("The user ${principal.userId} has successfully changed his/her password")
                            call.respond(HttpStatusCode.NoContent)
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
                    val localeData = runCatching { call.receiveNullable<LocaleChange>() }.getOrNull()
                        ?: throw ApiException(ExceptionReason.UNPARSABLE_DATA)

                    accountService.changeLocale(principal.userId, localeData.locale)
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
            }
        }

        get<Paths.UserOrganizations> { _ ->
            val principal = call.authentication.principal<ApiUser>()!!
            val userOrganizations = transactionMain {
                accountService.getRolesAssignedToUser(principal.userId).mapToArray { it.toApi() }
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
