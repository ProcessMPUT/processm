package processm.dbmodels.models

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `mostRecentData returns the correct value`() {
        val component = mockk<WorkspaceComponent> {
            every { data } returns """{"1": "a", "2": "b"}"""
        }
        assertEquals("b", component.mostRecentData())
    }

    @Test
    fun `mostRecentData ignores non-parsable keys`() {
        val component = mockk<WorkspaceComponent> {
            every { data } returns """{"blah": "a", "2": "b"}"""
        }
        assertEquals("b", component.mostRecentData())
    }

    @Test
    fun `mostRecentData takes the null key if all keys are non-parsable`() {
        val component = mockk<WorkspaceComponent> {
            every { data } returns """{"blah": "a", "null": "b"}"""
        }
        assertEquals("b", component.mostRecentData())
    }

    @Test
    fun `mostRecentData returns null if there is no suitable key`() {
        val component = mockk<WorkspaceComponent> {
            every { data } returns """{"blah": "a", "bloh": "b"}"""
        }
        assertNull(component.mostRecentData())
    }

    @Test
    fun `mostRecentData returns null if the object is not primitive`() {
        val component = mockk<WorkspaceComponent> {
            every { data } returns """{"1": ["p", "o", "r", "k"]}"""
        }
        assertNull(component.mostRecentData())
    }
}