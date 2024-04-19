package processm.services.api

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*
import kotlin.test.Test
import kotlin.test.assertTrue

class ApiExceptionTest {

    val PL = Locale("pl")

    // TODO I think these tests are not perfect, as there's an internal fallback to the system's locale

    @ParameterizedTest
    @EnumSource(ApiExceptionReason::class)
    fun `ApiExceptions are translated to Polish`(reason: ApiExceptionReason) {
        assertTrue { PL.getErrorMessage(reason.toString()).isNotBlank() }
    }

    @ParameterizedTest
    @EnumSource(ApiExceptionReason::class)
    fun `ApiExceptions are translated to English`(reason: ApiExceptionReason) {
        assertTrue { Locale.US.getErrorMessage(reason.toString()).isNotBlank() }
    }
}