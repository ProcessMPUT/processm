package processm.services.api
// Use this file to hold package-level internal functions that return receiver object passed to the `install` method.
import com.auth0.jwt.exceptions.TokenExpiredException
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.services.api.models.ErrorMessageBody
import processm.services.logic.ValidationException
import java.time.Duration
import java.util.*

internal fun ApplicationHstsConfiguration(): HSTS.Configuration.() -> Unit {
    return {
        maxAge = Duration.ofDays(365)
        includeSubDomains = true
        preload = false
        // You may also apply any custom directives supported by specific user-agent. For example:
        // customDirectives.put("redirectHttpToHttps", "false")
    }
}

internal fun ApplicationCompressionConfiguration(): Compression.Configuration.() -> Unit {
    return {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }
}

internal fun ApplicationStatusPageConfiguration(): StatusPages.Configuration.() -> Unit {
    return {
        logger().enter()
        exception<ValidationException> { cause ->
            val responseStatusCode = when (cause.reason) {
                ValidationException.Reason.ResourceAlreadyExists -> HttpStatusCode.Conflict
                ValidationException.Reason.ResourceNotFound -> HttpStatusCode.NotFound
                ValidationException.Reason.ResourceFormatInvalid -> HttpStatusCode.BadRequest
            }
            logger().trace(cause.message)
            call.respond(responseStatusCode, ErrorMessageBody(cause.userMessage))
        }
        exception<ApiException> { cause ->
            call.respond(cause.responseCode, ErrorMessageBody(cause.publicMessage.orEmpty()))
        }
        exception<TokenExpiredException> { cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorMessageBody(cause.message.orEmpty()))
        }
        exception<BadRequestException> { cause ->
            logger().error(cause)
            call.respond(HttpStatusCode.BadRequest)
        }
        exception<Exception> { cause ->
            logger().error(cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
        logger().exit()
    }
}

internal fun ApplicationAuthenticationConfiguration(
    config: ApplicationConfig
): Authentication.Configuration.() -> Unit {
    return {
        val jwtIssuer = config.property("issuer").getString()
        val jwtRealm = config.property("realm").getString()
        val jwtSecret = config.propertyOrNull("secret")?.getString() ?: JwtAuthentication.generateSecretKey()

        jwt {
            realm = jwtRealm
            verifier(JwtAuthentication.createVerifier(jwtIssuer, jwtSecret))
            validate { credentials -> ApiUser(credentials.payload.claims) }
        }
    }
}

internal fun ApplicationDataConversionConfiguration(): DataConversion.Configuration.() -> Unit {
    return {
        convert<UUID> {
            decode { values, _ ->
                values.singleOrNull().let { UUID.fromString(it) }
            }

            encode { value ->
                when (value) {
                    null -> listOf()
                    is UUID -> listOf(value.toString())
                    else -> throw DataConversionException("Cannot convert $value to UUID")
                }
            }
        }
    }
}
