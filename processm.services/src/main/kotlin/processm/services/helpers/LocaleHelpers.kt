package processm.services.helpers

import com.auth0.jwt.interfaces.Claim
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import processm.services.logic.ValidationException
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


/**
 * This field must be consistent with the resource bundles
 * Apparently there is no obvious way to list available translations and moreover available languages are more or less static,
 * so there's little gain to be had by computing this field in the runtime
 */
private val supportedLanguages = setOf("pl", "en")

@OptIn(ExperimentalContracts::class)
private fun isSupported(locale: Locale?): Boolean {
    contract {
        returns(true) implies (locale !== null)
    }
    return locale !== null && locale.language in supportedLanguages
}

data class LocalePrincipal(private val claims: Map<String, Claim>) : Principal {
    val locale: Locale? = claims["locale"]?.let { Locale.forLanguageTag(it.asString()) }
}

fun parseLocale(locale: String): Locale {
    val localeTags = locale.split("_", "-")
    val localeObject = when (localeTags.size) {
        3 -> Locale(localeTags[0], localeTags[1], localeTags[2])
        2 -> Locale(localeTags[0], localeTags[1])
        1 -> Locale(localeTags[0])
        else -> throw ValidationException(ExceptionReason.InvalidLocale)
    }

    try {
        localeObject.isO3Language
        localeObject.isO3Country
    } catch (e: MissingResourceException) {
        throw ValidationException(ExceptionReason.CannotChangeLocale, message = e.message)
    }

    return localeObject
}

/**
 * Reads the preferred locale from the user claims with a fall-back to the Accept-Language header, and then defaults to
 * en-US
 */
val ApplicationCall.locale: Locale
    get() {
        var locale = authentication.principal<LocalePrincipal>()?.locale
        if (isSupported(locale))
            return locale
        for (acceptLanguage in request.acceptLanguageItems()) {
            locale = parseLocale(acceptLanguage.value)
            if (isSupported(locale))
                return locale
        }
        return Locale.US
    }