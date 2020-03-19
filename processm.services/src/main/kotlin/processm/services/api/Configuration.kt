package processm.services.api

// Use this file to hold package-level internal functions that return receiver object passed to the `install` method.
import com.auth0.jwt.exceptions.TokenExpiredException
import io.ktor.application.call
import io.ktor.auth.OAuthServerSettings
import io.ktor.features.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.httpDateFormat
import io.ktor.response.respond
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.error
import org.apache.maven.wagon.authorization.AuthorizationException
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.services.api.models.ErrorResponse
import java.time.Duration
import java.util.concurrent.Executors


/**
 * Application block for [HSTS] configuration.
 *
 * This file may be excluded in .openapi-generator-ignore,
 * and application specific configuration can be applied in this function.
 *
 * See http://ktor.io/features/hsts.html
 */
internal fun ApplicationHstsConfiguration(): HSTS.Configuration.() -> Unit {
    return {
        maxAge = Duration.ofDays(365)
        includeSubDomains = true
        preload = false

        // You may also apply any custom directives supported by specific user-agent. For example:
        // customDirectives.put("redirectHttpToHttps", "false")
    }
}

/**
 * Application block for [Compression] configuration.
 *
 * This file may be excluded in .openapi-generator-ignore,
 * and application specific configuration can be applied in this function.
 *
 * See http://ktor.io/features/compression.html
 */
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
        exception<TokenExpiredException> { cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(cause.message))
        }
        exception<UnsupportedOperationException> { cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message))
        }
        exception<Exception> { cause ->
            logger().error(cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
        logger().exit()
    }
}

// Defines authentication mechanisms used throughout the application.
@KtorExperimentalAPI
val ApplicationAuthProviders: Map<String, OAuthServerSettings> = listOf<OAuthServerSettings>(
//        OAuthServerSettings.OAuth2ServerSettings(
//                name = "facebook",
//                authorizeUrl = "https://graph.facebook.com/oauth/authorize",
//                accessTokenUrl = "https://graph.facebook.com/oauth/access_token",
//                requestMethod = HttpMethod.Post,
//
//                clientId = "settings.property("auth.oauth.facebook.clientId").getString()",
//                clientSecret = "settings.property("auth.oauth.facebook.clientSecret").getString()",
//                defaultScopes = listOf("public_profile")
//        )
).associateBy { it.name }

// Provides an application-level fixed thread pool on which to execute coroutines (mainly)
internal val ApplicationExecutors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4)
