package processm.services.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.testing.*
import io.mockk.*
import org.awaitility.Awaitility.await
import org.jetbrains.exposed.dao.id.EntityID
import org.koin.test.mock.declareMock
import processm.dbmodels.models.*
import processm.dbmodels.models.Organization
import processm.services.api.models.*
import processm.services.logic.*
import java.util.*
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.*

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
        val accountService = declareMock<AccountService>()
        every { accountService.verifyUsersCredentials("user@example.com", "pass") } returns mockk {
            every { id } returns UUID.randomUUID()
            every { email } returns "user@example.com"
        }
        every { accountService.getRolesAssignedToUser(userId = any()) } returns
                listOf(mockk {
                    every { organization.id } returns EntityID(UUID.randomUUID(), Organizations)
                    every { role } returns RoleType.Owner.role
                })

        with(handleRequest(HttpMethod.Post, "/api/users/session") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            withSerializedBody(UserCredentials("user@example.com", "pass"))
        }) {
            assertEquals(HttpStatusCode.Created, response.status())
            assertTrue(response.deserializeContent<AuthenticationResult>().authorizationToken.isNotBlank())
        }

        verify { accountService.verifyUsersCredentials("user@example.com", "pass") }
    }

    @Test
    fun `responds to unsuccessful authentication with 401 and error message`() = withConfiguredTestApplication {
        val accountService = declareMock<AccountService>()

        every { accountService.verifyUsersCredentials(username = any(), password = any()) } returns null

        with(handleRequest(HttpMethod.Post, "/api/users/session") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            withSerializedBody(UserCredentials("user", "wrong_password"))
        }) {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
            assertTrue(response.deserializeContent<ErrorMessage>().error.contains("Invalid username or password"))
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
                        assertNotNull(response.deserializeContent<AuthenticationResult>().authorizationToken)
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
                assertTrue(response.deserializeContent<ErrorMessage>().error.contains("Either user credentials or authentication token needs to be provided"))
            }
        }

    @Test
    fun `responds to request with invalid token with 401`() = withConfiguredTestApplication {
        var currentToken: String? = null

        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/users")) {
                assertNotEquals(HttpStatusCode.Unauthorized, response.status())
                currentToken = request.header(HttpHeaders.Authorization)!!.substringAfter(' ')
            }
        }
        val randomizedToken = StringBuilder(assertNotNull(currentToken))

        // make this test deterministic
        val rand = Random(0)
        val validChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        do {
            randomizedToken[rand.nextInt(randomizedToken.indices)] = validChars.random(rand)
        } while (randomizedToken.toString() == currentToken)

        with(handleRequest(HttpMethod.Get, "/api/users") {
            addHeader(HttpHeaders.Authorization, "Bearer $randomizedToken")
        }) {
            // This test used to return 400 BadRequest because of malformed Authorization header and/or token.
            // The correct response is to response with 401 Unauthorized
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `responds to request with invalid Authorization header with 400`() = withConfiguredTestApplication {
        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/users")) {
                assertNotEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }

        with(handleRequest(HttpMethod.Get, "/api/users") {
            addHeader(HttpHeaders.Authorization, "Bearer <just<invalid#token&s=symbols")
        }) {
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun `responds to request with invalid Authorization method with 401`() = withConfiguredTestApplication {
        val login = "user@example.com"
        val password = "passW0RD"
        withAuthentication(login = login, password = password) {
            with(handleRequest(HttpMethod.Get, "/api/users")) {
                assertNotEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }

        with(handleRequest(HttpMethod.Get, "/api/users") {
            val credentials = Base64.getEncoder().encodeToString("$login:$password".toByteArray())
            assertEquals("dXNlckBleGFtcGxlLmNvbTpwYXNzVzBSRA==", credentials)
            addHeader(HttpHeaders.Authorization, "Basic $credentials")
        }) {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `responds to users list request with 200 and users list`() = withConfiguredTestApplication {
        val accountService = declareMock<AccountService>()

        val uuid = UUID.randomUUID()
        val email = "demo@example.com"
        every { accountService.getUsers(uuid, null, any()) } returns listOf(
            mockk {
                every { id } returns EntityID(uuid, Users)
                every { this@mockk.email } returns email
                every { locale } returns "en_US"
                every { privateGroup } returns mockk {
                    every { id } returns EntityID(UUID.randomUUID(), Groups)
                    every { name } returns "email"
                    every { isImplicit } returns true
                }
            })

        withAuthentication(uuid, email) {
            with(handleRequest(HttpMethod.Get, "/api/users")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.deserializeContent<List<UserInfo>>())
            }
        }

        verify { accountService.getUsers(uuid, null, any()) }
    }

    @Test
    fun `responds to current user details request with 200 and current user account details`() =
        withConfiguredTestApplication {
            val accountService = declareMock<AccountService>()
            val uuid = UUID.randomUUID()

            every { accountService.getUser(userId = uuid) } returns mockk {
                every { id } returns EntityID(uuid, Users)
                every { email } returns "user@example.com"
                every { locale } returns "en_US"
            }

            withAuthentication(uuid) {
                with(handleRequest(HttpMethod.Get, "/api/users/me")) {
                    assertEquals(HttpStatusCode.OK, response.status())
                    val account = response.deserializeContent<UserAccountInfo>()
                    assertEquals(uuid, account.id)
                    assertEquals("user@example.com", account.email)
                    assertEquals("en_US", account.locale)
                }
            }

            verify { accountService.getUser(userId = uuid) }
        }

    @Test
    fun `responds to non-existing user details request with 404 and error message`() =
        withConfiguredTestApplication {
            val accountService = declareMock<AccountService>()

            every { accountService.getUser(userId = any()) } throws ValidationException(
                Reason.ResourceNotFound, "Specified user account does not exist"
            )

            withAuthentication {
                with(handleRequest(HttpMethod.Get, "/api/users/me")) {
                    assertEquals(HttpStatusCode.NotFound, response.status())
                    assertTrue(response.deserializeContent<ErrorMessage>().error.contains("Specified user account does not exist"))
                }
            }

            verify { accountService.getUser(userId = any()) }
        }

    @Test
    fun `responds to successful account registration attempt with 201`() = withConfiguredTestApplication {
        val accountService = declareMock<AccountService>()
        val organizationService = declareMock<OrganizationService>()
        val user = mockk<User> {
            every { id } returns EntityID<UUID>(UUID.randomUUID(), Users)
            every { email } returns "user@example.com"
        }
        val organization = mockk<Organization> {
            every { id } returns EntityID<UUID>(UUID.randomUUID(), Organizations)
            every { name } returns "OrgName1"
        }
        every {
            accountService.create(
                "user@example.com", accountLocale = any(), pass = any()
            )
        } returns user

        every {
            organizationService.create("OrgName1", true, null)
        } returns organization

        every {
            organizationService.addMember(organization.id.value, user.id.value, RoleType.Owner)
        } returns user

        withAuthentication {
            with(handleRequest(HttpMethod.Post, "/api/users") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                withSerializedBody(
                    AccountRegistrationInfo(
                        userEmail = "user@example.com",
                        userPassword = "pass",
                        newOrganization = true,
                        organizationName = "OrgName1"
                    )
                )
            }) {
                assertEquals(HttpStatusCode.Created, response.status())
            }
        }

        verify(exactly = 1) {
            accountService.create("user@example.com", accountLocale = null, pass = "pass")
            organizationService.create("OrgName1", isPrivate = true, parent = null)
            organizationService.addMember(organization.id.value, user.id.value, RoleType.Owner)
        }
    }

    @Test
    fun `responds to account registration attempt with already existing user or organization with 409 and error message`() =
        withConfiguredTestApplication {
            val accountService = declareMock<AccountService>()

            every {
                accountService.create(
                    "user@example.com", accountLocale = any(), pass = any()
                )
            } throws ValidationException(
                Reason.ResourceAlreadyExists,
                "User with specified name already exists"
            )

            withAuthentication {
                with(handleRequest(HttpMethod.Post, "/api/users") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(
                        AccountRegistrationInfo(
                            userEmail = "user@example.com",
                            userPassword = "pass",
                            newOrganization = true,
                            organizationName = "OrgName1"
                        )
                    )
                }) {
                    assertEquals(HttpStatusCode.Conflict, response.status())
                    assertTrue(response.deserializeContent<ErrorMessage>().error.contains("User with specified name already exists"))
                }
            }

            verify { accountService.create("user@example.com", null, "pass") }
        }

    @Test
    fun `responds to account registration attempt with invalid data with 400 and error message`() =
        withConfiguredTestApplication {
            val accountService = declareMock<AccountService>()
            val organizationService = declareMock<OrganizationService>()
            withAuthentication {
                with(handleRequest(HttpMethod.Post, "/api/users") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(Object())
                }) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                    assertTrue(response.deserializeContent<ErrorMessage>().error.contains("The provided account details cannot be parsed"))
                }
            }

            verify(exactly = 0) {
                accountService.create(
                    email = any(),
                    pass = any(),
                    accountLocale = any()
                )
            }

            verify(exactly = 0) {
                organizationService.create(
                    name = any(),
                    isPrivate = any(),
                    parent = any()
                )
            }
        }

    @Test
    fun `responds to successful password change with 200`() = withConfiguredTestApplication {
        val accountService = declareMock<AccountService>()

        every {
            accountService.changePassword(
                userId = any(), currentPassword = "current", newPassword = "new"
            )
        } returns true

        withAuthentication {
            with(handleRequest(HttpMethod.Patch, "/api/users/me/password") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                withSerializedBody(PasswordChange("current", "new"))
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
            val accountService = declareMock<AccountService>()

            every {
                accountService.changePassword(
                    userId = any(), currentPassword = "wrong_password", newPassword = "new"
                )
            } returns false

            withAuthentication {
                with(handleRequest(HttpMethod.Patch, "/api/users/me/password") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(PasswordChange("wrong_password", "new"))
                }) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    assertTrue(response.deserializeContent<ErrorMessage>().error.contains("The current password could not be changed"))
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
            val accountService = declareMock<AccountService>()
            withAuthentication {
                with(handleRequest(HttpMethod.Patch, "/api/users/me/password") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(Object())
                }) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                    assertTrue(response.deserializeContent<ErrorMessage>().error.contains("The provided password data cannot be parsed"))
                }
            }

            verify(exactly = 0) {
                accountService.changePassword(
                    userId = any(), currentPassword = any(), newPassword = any()
                )
            }
        }

    @Test
    fun `responds to successful locale change with 204`() = withConfiguredTestApplication {
        val accountService = declareMock<AccountService>()

        every { accountService.changeLocale(userId = any(), locale = "pl_PL") } just runs

        withAuthentication {
            with(handleRequest(HttpMethod.Patch, "/api/users/me/locale") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                withSerializedBody(LocaleChange("pl_PL"))
            }) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }

        verify { accountService.changeLocale(userId = any(), locale = "pl_PL") }
    }

    @Test
    fun `responds to locale change attempt with invalid locale format with 400 and error message`() =
        withConfiguredTestApplication {
            val accountService = declareMock<AccountService>()

            every {
                accountService.changeLocale(
                    userId = any(), locale = "eng_ENG"
                )
            } throws ValidationException(
                Reason.ResourceFormatInvalid, "The current locale could not be changed"
            )

            withAuthentication {
                with(handleRequest(HttpMethod.Patch, "/api/users/me/locale") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(LocaleChange("eng_ENG"))
                }) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                    assertTrue(response.deserializeContent<ErrorMessage>().error.contains("The current locale could not be changed"))
                }
            }

            verify { accountService.changeLocale(userId = any(), locale = "eng_ENG") }
        }

    @Test
    fun `responds to locale change attempt with invalid data with 400 and error message`() =
        withConfiguredTestApplication {
            val accountService = declareMock<AccountService>()
            withAuthentication {
                with(handleRequest(HttpMethod.Patch, "/api/users/me/locale") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(Object())
                }) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                    assertTrue(response.deserializeContent<ErrorMessage>().error.contains("The provided locale data cannot be parsed"))
                }
            }

            verify(exactly = 0) { accountService.changeLocale(userId = any(), locale = any()) }
        }

    @Test
    fun `responds to user roles request with 200 and user roles list`() =
        withConfiguredTestApplication {
            val accountService = declareMock<AccountService>()
            val userId = UUID.randomUUID()
            withAuthentication(userId) {
                every { accountService.getRolesAssignedToUser(userId) } returns
                        listOf(mockk {
                            every { user.id } returns EntityID(userId, Users)
                            every { organization.id } returns EntityID(UUID.randomUUID(), Organizations)
                            every { organization.name } returns "Org1"
                            every { organization.isPrivate } returns false
                            every { role } returns RoleType.Writer.role
                        })

                with(handleRequest(HttpMethod.Get, "/api/users/me/organizations")) {
                    assertEquals(HttpStatusCode.OK, response.status())
                    val deserializedContent =
                        response.deserializeContent<List<ApiOrganization>>()
                    assertEquals(1, deserializedContent.count())
                    assertTrue {
                        deserializedContent.any { it.name == "Org1" }
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
}
