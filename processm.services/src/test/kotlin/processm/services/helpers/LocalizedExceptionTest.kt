package processm.services.helpers

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*
import kotlin.test.assertTrue

class LocalizedExceptionTest {

    val PL = Locale("pl")

    // TODO I think these tests are not perfect, as there's an internal fallback to the system's locale

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