package processm.services.helpers

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*
import kotlin.test.assertTrue

/**
 * I think these tests are not perfect, as it seems there's an internal fallback to the system's locale
 * Still, they are better than nothing
 */
class LocalizedExceptionTest {

    val PL = Locale("pl")

    @ParameterizedTest
    @EnumSource(ExceptionReason::class)
    fun `ExceptionReasons are translated to Polish`(reason: ExceptionReason) {
        assertTrue { PL.getErrorMessage(reason.toString()).isNotBlank() }
    }

    @ParameterizedTest
    @EnumSource(ExceptionReason::class)
    fun `ExceptionReasons are translated to English`(reason: ExceptionReason) {
        assertTrue { Locale.US.getErrorMessage(reason.toString()).isNotBlank() }
    }
}