package processm.etl.jdbc.nosql

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JsonPathTest {

    @Test
    fun `simple path`() {
        assertEquals(listOf("a", "b", "c"), JsonPath("a.b.c").items)
    }

    @Test
    fun `escaped dot`() {
        assertEquals(listOf("a.", "b", "c"), JsonPath("a\\..b.c").items)
    }

    @Test
    fun quoted() {
        assertEquals(listOf("a.b", "c"), JsonPath("\"a.b\".c").items)
    }

    @Test
    fun `quot in quoted`() {
        assertEquals(listOf("a\".b", "c"), JsonPath("\"a\\\".b\".c").items)
    }

    @Test
    fun `dangling escape throws`() {
        assertThrows<IllegalStateException> { JsonPath("a\\") }
    }

    @Test
    fun `dangling quotation throws`() {
        assertThrows<IllegalStateException> { JsonPath("\"a") }
    }

    @Test
    fun `get follows the path`() {
        val path = JsonPath(listOf("a", "b", "c"))
        val doc: JsonElement = Json.parseToJsonElement(
            """
            {
            "a": {"b":  {"c":  "d"}},
            "a.b": {"c":  "e"},
            "a.b.c":   "f"
            }
        """.trimIndent()
        )
        with(doc[path]) {
            assertIs<JsonPrimitive>(this)
            assertEquals("d", content)
        }
    }

    @Test
    fun `get handles arrays`() {
        val path = JsonPath(listOf("1"))
        val doc: JsonElement = Json.parseToJsonElement(
            """
            ["a", "b", "c"]
        """.trimIndent()
        )
        with(doc[path]) {
            assertIs<JsonPrimitive>(this)
            assertEquals("b", content)
        }
    }

    @Test
    fun `get handles out of bounds indices gracefully`() {
        val doc: JsonElement = Json.parseToJsonElement(
            """
            ["a", "b", "c"]
        """.trimIndent()
        )
        assertNull(doc[JsonPath("-1")])
        assertNull(doc[JsonPath("777")])
        assertNull(doc[JsonPath("ziemniak")])
    }

    @Test
    fun `toString does not escape if not necessary`() {
        assertEquals("a.b.c", JsonPath(listOf("a", "b", "c")).toString())
    }

    @Test
    fun `toString escapes if dot`() {
        assertEquals("a\\.b.c", JsonPath(listOf("a.b", "c")).toString())
    }

    @Test
    fun `toString escapes if quotation mark`() {
        assertEquals("a\\\"b.c", JsonPath(listOf("a\"b", "c")).toString())
    }

    @Test
    fun `toString escapes if backslash`() {
        assertEquals("a\\\\b.c", JsonPath(listOf("a\\b", "c")).toString())
    }
}