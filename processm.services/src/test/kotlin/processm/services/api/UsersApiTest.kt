package processm.services.api

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.server.testing.handleRequest
import io.mockk.*
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.TestInstance
import processm.services.api.models.*
import processm.services.logic.ValidationException
import processm.services.models.OrganizationRoleDto
import java.util.*
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsersApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/users",
        HttpMethod.Delete to "/api/users/session",
        HttpMethod.Get to "/api/users/me",
        HttpMethod.Patch to "/api/users/me/password",
        HttpMethod.Patch to "/api/users/me/locale"
    )

    override fun endpointsWithNoImplementation() = Stream.of<Pair<HttpMethod, String>?>(null)

    @Test
    fun `responds to successful authentication with 201 and token`() = withConfiguredTestApplication {
        every { accountService.verifyUsersCredentials("user@example.com", "pass") } returns mockk {
            every { id } returns UUID.randomUUID()
            every { email } returns "user@example.com"
        }
        every { accountService.getRolesAssignedToUser(userId = any()) } returns listOf(
            mockk {
                every { organization.id } returns UUID.randomUUID()
                every { this@mockk.role } returns OrganizationRoleDto.Owner
            })

        with(handleRequest(HttpMethod.Post, "/api/users/session") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            withSerializedBody(UserCredentialsMessageBody(UserCredentials("user@example.com", "pass")))
        }) {
            assertEquals(HttpStatusCode.Created, response.status())
            assertTrue(response.deserializeContent<AuthenticationResultMessageBody>().data.authorizationToken.isNotBlank())
        }

        verify { accountService.verifyUsersCredentials("user@example.com", "pass") }
    }

    @Test
    fun `responds to unsuccessful authentication with 401 and error message`() = withConfiguredTestApplication {
        every { accountService.verifyUsersCredentials(username = any(), password = any()) } returns null

        with(handleRequest(HttpMethod.Post, "/api/users/session") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            withSerializedBody(UserCredentialsMessageBody(UserCredentials("user", "wrong_password")))
        }) {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
            assertTrue(response.deserializeContent<ErrorMessageBody>().error.contains("Invalid username or password"))
        }

        verify { accountService.verifyUsersCredentials(username = any(), password = any()) }
    }

    @Test
    fun `responds to request with expired token with 401`() = withConfiguredTestApplication({
        // token expires one second after creation
        put("ktor.jwt.tokenTtl", "PT1S")
    }) {
        withAuthentication {
            // wait till the current token expires
            await().until {
                with(handleRequest(HttpMethod.Get, "/api/users")) {
                    response.status() == HttpStatusCode.Unauthorized
                }
            }

            with(handleRequest(HttpMethod.Get, "/api/users")) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `responds to authentication request with valid expired token with 201 and renewed token`() =
        withConfiguredTestApplication({
            // token expires two seconds after creation
            put("ktor.jwt.tokenTtl", "PT2S")
        }) {
            var renewedToken: String? = null

            withAuthentication {
                // wait till the current token expires
                await().until {
                    with(handleRequest(HttpMethod.Get, "/api/users")) {
                        response.status() == HttpStatusCode.Unauthorized
                    }
                }
                // make sure the token is expired
                with(handleRequest(HttpMethod.Get, "/api/users")) {
                    assertEquals(HttpStatusCode.Unauthorized, response.status())
                }
                // renew the token
                with(handleRequest(HttpMethod.Post, "/api/users/session")) {
                    assertEquals(HttpStatusCode.Created, response.status())
                    renewedToken =
                        assertNotNull(response.deserializeContent<AuthenticationResultMessageBody>().data.authorizationToken)
                }
            }

            assertNotNull(renewedToken)
            // make sure the token is valid
            with(handleRequest(HttpMethod.Get, "/api/users") {
                addHeader(HttpHeaders.Authorization, "Bearer $renewedToken")
            }) {
                assertNotEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }

    @Test
    fun `responds to authentication request without credentials or token with 400 and error message`() =
        withConfiguredTestApplication {
            with(handleRequest(HttpMethod.Post, "/api/users/session")) {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error.contains("Either user credentials or authentication token needs to be provided"))
            }
        }

    @Test
    fun `responds to request with malformed token with 401`() = withConfiguredTestApplication {
        var currentToken: String? = null

        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/users")) {
                assertNotEquals(HttpStatusCode.Unauthorized, response.status())
                currentToken = request.header(HttpHeaders.Authorization)
            }
        }
        var randomizedToken = StringBuilder(assertNotNull(currentToken))

        do {
            repeat(20) {
                randomizedToken[Random.nextInt(randomizedToken.indices)] = ('A'..'z').random()
            }
        } while (randomizedToken.toString() == currentToken)

        with(handleRequest(HttpMethod.Get, "/api/users") {
            addHeader(HttpHeaders.Authorization, "Bearer $randomizedToken")
        }) {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `responds to users list request with 200 and users list`() = withConfiguredTestApplication {
        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/users")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.deserializeContent<UserInfoCollectionMessageBody>().data)
            }
        }
    }

    @Test
    fun `responds to current user details request with 200 and current user account details`() =
        withConfiguredTestApplication {
            every { accountService.getAccountDetails(userId = any()) } returns mockk {
                every { email } returns "user@example.com"
                every { locale } returns "en_US"
            }

            withAuthentication {
                with(handleRequest(HttpMethod.Get, "/api/users/me")) {
                    assertEquals(HttpStatusCode.OK, response.status())
                    val deserializedContent = response.deserializeContent<UserAccountInfoMessageBody>()
                    assertEquals("user@example.com", deserializedContent.data.userEmail)
                    assertEquals("en_US", deserializedContent.data.locale)
                }
            }

            verify { accountService.getAccountDetails(userId = any()) }
        }

    @Test
    fun `responds to non-existing user details request with 404 and error message`() = withConfiguredTestApplication {
        every { accountService.getAccountDetails(userId = any()) } throws ValidationException(
            ValidationException.Reason.ResourceNotFound, "Specified user account does not exist"
        )

        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/users/me")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error.contains("Specified user account does not exist"))
            }
        }

        verify { accountService.getAccountDetails(userId = any()) }
    }

    @Test
    fun `responds to successful account registration attempt with 201`() = withConfiguredTestApplication {
        every {
            accountService.createAccount(
                "user@example.com", "OrgName1", accountLocale = any()
            )
        } just Runs

        withAuthentication {
            with(handleRequest(HttpMethod.Post, "/api/users") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                withSerializedBody(
                    AccountRegistrationInfoMessageBody(
                        AccountRegistrationInfo(
                            "user@example.com", "OrgName1"
                        )
                    )
                )
            }) {
                assertEquals(HttpStatusCode.Created, response.status())
            }
        }

        verify { accountService.createAccount("user@example.com", "OrgName1") }
    }

    @Test
    fun `responds to account registration attempt with already existing user or organization with 409 and error message`() =
        withConfiguredTestApplication {
            every {
                accountService.createAccount(
                    "user@example.com", "OrgName1", accountLocale = any()
                )
            } throws ValidationException(
                ValidationException.Reason.ResourceAlreadyExists,
                "User and/or organization with specified name already exists"
            )

            withAuthentication {
                with(handleRequest(HttpMethod.Post, "/api/users") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(
                        AccountRegistrationInfoMessageBody(
                            AccountRegistrationInfo(
                                "user@example.com", "OrgName1"
                            )
                        )
                    )
                }) {
                    assertEquals(HttpStatusCode.Conflict, response.status())
                    assertTrue(response.deserializeContent<ErrorMessageBody>().error.contains("User and/or organization with specified name already exists"))
                }
            }

            verify { accountService.createAccount("user@example.com", "OrgName1") }
        }

    @Test
    fun `responds to account registration attempt with invalid data with 400 and error message`() =
        withConfiguredTestApplication {
            withAuthentication {
                with(handleRequest(HttpMethod.Post, "/api/users") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(Object())
                }) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                    assertTrue(response.deserializeContent<ErrorMessageBody>().error.contains("The provided account details cannot be parsed"))
                }
            }

            verify(exactly = 0) { accountService.createAccount(userEmail = any(), organizationName = any()) }
        }

    @Test
    fun `responds to successful password change with 200`() = withConfiguredTestApplication {
        every {
            accountService.changePassword(
                userId = any(), currentPassword = "current", newPassword = "new"
            )
        } returns true

        withAuthentication {
            with(handleRequest(HttpMethod.Patch, "/api/users/me/password") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                withSerializedBody(PasswordChangeMessageBody(PasswordChange("current", "new")))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }

        verify {
            accountService.changePassword(
                userId = any(), currentPassword = "current", newPassword = "new"
            )
        }
    }

    @Test
    fun `responds to password change attempt with incorrect password with 403 and error message`() =
        withConfiguredTestApplication {
            every {
                accountService.changePassword(
                    userId = any(), currentPassword = "wrong_password", newPassword = "new"
                )
            } returns false

            withAuthentication {
                with(handleRequest(HttpMethod.Patch, "/api/users/me/password") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(PasswordChangeMessageBody(PasswordChange("wrong_password", "new")))
                }) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    assertTrue(response.deserializeContent<ErrorMessageBody>().error.contains("The current password could not be changed"))
                }
            }

            verify {
                accountService.changePassword(
                    userId = any(), currentPassword = "wrong_password", newPassword = "new"
                )
            }
        }

    @Test
    fun `responds to password change attempt with invalid data with 400 and error message`() =
        withConfiguredTestApplication {
            withAuthentication {
                with(handleRequest(HttpMethod.Patch, "/api/users/me/password") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(Object())
                }) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                    assertTrue(response.deserializeContent<ErrorMessageBody>().error.contains("The provided password data cannot be parsed"))
                }
            }

            verify(exactly = 0) {
                accountService.changePassword(
                    userId = any(), currentPassword = any(), newPassword = any()
                )
            }
        }

    @Test
    fun `responds to successful locale change with 200`() = withConfiguredTestApplication {
        every { accountService.changeLocale(userId = any(), locale = "pl_PL") } just runs

        withAuthentication {
            with(handleRequest(HttpMethod.Patch, "/api/users/me/locale") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                withSerializedBody(LocaleChangeMessageBody(LocaleChange("pl_PL")))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }

        verify { accountService.changeLocale(userId = any(), locale = "pl_PL") }
    }

    @Test
    fun `responds to locale change attempt with invalid locale format with 400 and error message`() =
        withConfiguredTestApplication {
            every {
                accountService.changeLocale(
                    userId = any(), locale = "eng_ENG"
                )
            } throws ValidationException(
                ValidationException.Reason.ResourceFormatInvalid, "The current locale could not be changed"
            )

            withAuthentication {
                with(handleRequest(HttpMethod.Patch, "/api/users/me/locale") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(LocaleChangeMessageBody(LocaleChange("eng_ENG")))
                }) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                    assertTrue(response.deserializeContent<ErrorMessageBody>().error.contains("The current locale could not be changed"))
                }
            }

            verify { accountService.changeLocale(userId = any(), locale = "eng_ENG") }
        }

    @Test
    fun `responds to locale change attempt with invalid data with 400 and error message`() =
        withConfiguredTestApplication {
            withAuthentication {
                with(handleRequest(HttpMethod.Patch, "/api/users/me/locale") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(Object())
                }) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                    assertTrue(response.deserializeContent<ErrorMessageBody>().error.contains("The provided locale data cannot be parsed"))
                }
            }

            verify(exactly = 0) { accountService.changeLocale(userId = any(), locale = any()) }
        }

    @Test
    fun `responds to user roles request with 200 and user roles list`() =
        withConfiguredTestApplication {
            val userId = UUID.randomUUID()
            withAuthentication(userId) {
                every { accountService.getRolesAssignedToUser(userId) } returns listOf(
                        mockk {
                            every { user.id } returns userId
                            every { organization.id } returns UUID.randomUUID()
                            every { organization.name } returns "Org1"
                            every { role } returns OrganizationRoleDto.Writer
                        }
                    )

                with(handleRequest(HttpMethod.Get, "/api/users/me/organizations")) {
                    assertEquals(HttpStatusCode.OK, response.status())
                    val deserializedContent = response.deserializeContent<UserOrganizationCollectionMessageBody>()
                    assertEquals(1, deserializedContent.data.count())
                    assertTrue { deserializedContent.data.any {it.name == "Org1" && it.organizationRole == OrganizationRole.writer }}
                }
            }

            verify { accountService.getRolesAssignedToUser(userId = any()) }
        }

    @Test
    fun `responds to user session termination attempt with 204`() = withConfiguredTestApplication {
        withAuthentication {
            with(handleRequest(HttpMethod.Delete, "/api/users/session")) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }
    }
}
