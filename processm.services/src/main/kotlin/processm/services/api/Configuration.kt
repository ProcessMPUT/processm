package processm.services.api
// Use this file to hold package-level internal functions that return receiver object passed to the `install` method.
import com.auth0.jwt.exceptions.TokenExpiredException
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.hsts.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.util.logging.*
import processm.core.logging.loggedScope
import processm.services.api.models.ErrorMessage
import processm.services.logic.Reason
import processm.services.logic.ValidationException
import java.time.Duration
import java.util.*
import io.ktor.util.converters.DataConversion.Configuration as DataConversionConfig

internal fun ApplicationHstsConfiguration(): HSTSConfig.() -> Unit = {
    maxAgeInSeconds = Duration.ofDays(365).toSeconds()
    includeSubDomains = true
    preload = false
    // You may also apply any custom directives supported by specific user-agent. For example:
    // customDirectives.put("redirectHttpToHttps", "false")
}

internal fun ApplicationCompressionConfiguration(): CompressionConfig.() -> Unit = {
    gzip {
        priority = 1.0
    }
    deflate {
        priority = 10.0
        minimumSize(1024) // condition
    }
}

internal fun ApplicationStatusPageConfiguration(): StatusPagesConfig.() -> Unit = {
    loggedScope { logger ->
        exception<ValidationException> { call, cause ->
            val responseStatusCode = when (cause.reason) {
                Reason.ResourceAlreadyExists -> HttpStatusCode.Conflict
                Reason.ResourceNotFound -> HttpStatusCode.NotFound
                Reason.ResourceFormatInvalid -> HttpStatusCode.BadRequest
                Reason.UnprocessableResource -> HttpStatusCode.UnprocessableEntity
                Reason.Unauthorized -> HttpStatusCode.Unauthorized
            }
            logger.trace(cause.message)
            call.respond(responseStatusCode, ErrorMessage(cause.userMessage))
        }
        exception<ApiException> { call, cause ->
            call.respond(cause.responseCode, ErrorMessage(cause.publicMessage.orEmpty()))
        }
        exception<TokenExpiredException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorMessage(cause.message.orEmpty()))
        }
        exception<BadRequestException> { call, cause ->
            logger.error(cause)
            call.respond(HttpStatusCode.BadRequest)
        }
        exception<Exception> { call, cause ->
            logger.error(cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}

internal fun ApplicationAuthenticationConfiguration(config: ApplicationConfig): AuthenticationConfig.() -> Unit = {
    val jwtIssuer = config.property("issuer").getString()
    val jwtRealm = config.property("realm").getString()
    val jwtSecret = JwtAuthentication.getSecretKey(config)

    jwt {
        realm = jwtRealm
        verifier(JwtAuthentication.createVerifier(jwtIssuer, jwtSecret))
        validate { credentials -> ApiUser(credentials.payload.claims) }
    }
}

internal fun ApplicationDataConversionConfiguration(): DataConversionConfig.() -> Unit {
    return {
        convert<UUID> {
            decode { values ->
                values.singleOrNull().let { UUID.fromString(it) }
            }

            encode { value ->
                listOf(value.toString())
            }
        }
    }
}
