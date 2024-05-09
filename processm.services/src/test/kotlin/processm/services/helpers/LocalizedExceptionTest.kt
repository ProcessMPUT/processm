package processm.services.helpers

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import processm.core.querylanguage.PQLParserError
import processm.core.querylanguage.PQLSyntaxError
import processm.helpers.getErrorMessage
import java.util.*
import kotlin.test.assertTrue

/**
 * I think these tests are not perfect, as it seems there's an internal fallback to the system's locale
 * Still, they are better than nothing
 */
class LocalizedExceptionTest {

    companion object {
        @JvmStatic
        fun listReasons(): List<Arguments> =
            listOf(Locale.US, Locale("pl")).flatMap { locale ->
                listOf(
                    ExceptionReason.values(),
                    PQLSyntaxError.Problem.values(),
                    PQLParserError.Problem.values()
                ).flatMap { enum ->
                    enum.map { Arguments.of(locale, it.toString()) }
                }
            }

    }

    @ParameterizedTest
    @MethodSource("listReasons")
    fun `translation is not blank`(locale: Locale, reason: String) {
        assertTrue { locale.getErrorMessage(reason).isNotBlank() }
    }
}