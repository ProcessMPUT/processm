package processm.services.helpers

import io.ktor.http.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*
import kotlin.test.Test
import kotlin.test.assertTrue

enum class TestEnum(val statusCode: HttpStatusCode = HttpStatusCode.BadRequest) {
    A,
    B(HttpStatusCode.Forbidden)
}

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

    @Test
    fun test() {
        println(TestEnum.A)
        println(TestEnum.B)
    }
}