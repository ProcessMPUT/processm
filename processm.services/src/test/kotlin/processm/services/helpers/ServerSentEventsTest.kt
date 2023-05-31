package processm.services.helpers

import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

@ServerSentEvent("someName")
@Serializable
data class SomeEvent(val value: String)

class ServerSentEventsTest {

    @Test
    fun `writeEvent with annotated class`() {
        val baseStream = ByteArrayOutputStream()
        EventStream(baseStream).writeEvent(SomeEvent("a value"))
        val text = baseStream.toByteArray().decodeToString()
        assertEquals(
            """event:someName
            |data:{"value":"a value"}
            |
            |""".trimMargin(), text
        )
    }
}