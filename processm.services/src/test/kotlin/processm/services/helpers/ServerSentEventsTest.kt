package processm.services.helpers

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

@ServerSentEvent("someName")
@Serializable
data class SomeEvent(val value: String)

/**
 * Reverse the application of [escapeNewLine]
 */
internal fun unescapeNewLine(input: String): String =
    buildString {
        var i = 0
        while (i < input.length) {
            if (input[i] == '\\') {
                i++
                if (i >= input.length) throw IllegalArgumentException("There is a dangling \\ at the end of the string")
                when (input[i]) {
                    '\\' -> append('\\')
                    'n' -> append('\n')
                    else -> throw IllegalArgumentException("Unknown escaped character ${input[i + 1]}")
                }
                i++
            } else
                append(input[i++])
        }
    }

class ServerSentEventsTest {

    @Test
    fun `writeEvent with annotated class`() = runBlocking {
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

    @Test
    fun escapeNewLine() {
        assertEquals("\\\\", escapeNewLine("\\"))
        assertEquals("\\\\\\\\", escapeNewLine("\\\\"))
        assertEquals("\\\\\\\\n", escapeNewLine("\\\\n"))
        assertEquals("\\\\\\\\\\n", escapeNewLine("\\\\\n"))
    }

    @Test
    fun `escape and unescape`() {
        assertEquals("\\", unescapeNewLine(escapeNewLine("\\")))
        assertEquals("\\\\", unescapeNewLine(escapeNewLine("\\\\")))
        assertEquals("\\\\n", unescapeNewLine(escapeNewLine("\\\\n")))
        assertEquals("\\\\\n", unescapeNewLine(escapeNewLine("\\\\\n")))
    }
}
