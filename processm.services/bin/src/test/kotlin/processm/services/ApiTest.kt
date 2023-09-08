package processm.services

import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiTest {
    @Test
    fun `responds to direct request to api path with 301 and api documentation location`() = withTestApplication {
        (environment.config as MapApplicationConfig).apply {
            put("ktor.jwt.issuer", "issuer")
            put("ktor.jwt.realm", "test")
            put("ktor.jwt.secret", "secretkey123")
            put("ktor.jwt.tokenTtl", "PT10S")
        }
        application.apiModule()

        with(handleRequest(HttpMethod.Get, "/api")) {
            assertEquals(HttpStatusCode.MovedPermanently, response.status())
            assertEquals("/api-docs/", response.headers[HttpHeaders.Location])
        }
    }
}
