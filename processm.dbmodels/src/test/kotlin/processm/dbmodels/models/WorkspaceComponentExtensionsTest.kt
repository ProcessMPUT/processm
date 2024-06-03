package processm.dbmodels.models

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WorkspaceComponentExtensionsTest {

    @Test
    fun `mostRecentVersion ignores non-integer keys`() {
        assertEquals(1L, listOf("blah", "1").mostRecentVersion())
    }

    @Test
    fun `mostRecentVersion takes the maximum`() {
        assertEquals(3L, listOf("2", "3", "1").mostRecentVersion())
    }

    @Test
    fun `mostRecentVersion returns null for empty list`() {
        assertEquals(null, emptyList<String>().mostRecentVersion())
    }

    @Test
    fun `mostRecentVersion returns null for a list of non-parsable keys`() {
        assertEquals(null, listOf("a", "b", "c").mostRecentVersion())
    }

    @Test
    fun `mostRecentVersion returns null for a list with null string`() {
        assertEquals(null, listOf("null").mostRecentVersion())
    }
}
