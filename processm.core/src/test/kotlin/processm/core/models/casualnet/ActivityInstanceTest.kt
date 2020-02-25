package processm.core.models.casualnet

import processm.core.models.causalnet.Activity
import processm.core.models.causalnet.ActivityInstance
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityInstanceTest {

    @Test
    fun sameActivityNoId() {
        val a1 = ActivityInstance(Activity("a"))
        val a2 = ActivityInstance(Activity("a"))
        assertTrue { a1 == a2 }
        assertTrue { a1.hashCode() == a2.hashCode() }
    }

    @Test
    fun differentActivityNoId() {
        val a1 = ActivityInstance(Activity("a"))
        val a2 = ActivityInstance(Activity("b"))
        assertFalse { a1 == a2 }
        assertFalse { a1.hashCode() == a2.hashCode() }
    }

    @Test
    fun sameActivitySameId() {
        val a1 = ActivityInstance(Activity("a"), "1")
        val a2 = ActivityInstance(Activity("a"), "1")
        assertTrue { a1 == a2 }
        assertTrue { a1.hashCode() == a2.hashCode() }
    }

    @Test
    fun sameActivityDifferentId() {
        val a1 = ActivityInstance(Activity("a"), "1")
        val a2 = ActivityInstance(Activity("a"), "2")
        assertFalse { a1 == a2 }
        assertFalse { a1.hashCode() == a2.hashCode() }
    }

    @Test
    fun differentActivitySameId() {
        val a1 = ActivityInstance(Activity("a"), "1")
        val a2 = ActivityInstance(Activity("b"), "1")
        assertFalse { a1 == a2 }
        assertFalse { a1.hashCode() == a2.hashCode() }
    }

    @Test
    fun differentActivityDifferentId() {
        val a1 = ActivityInstance(Activity("a"), "1")
        val a2 = ActivityInstance(Activity("b"), "2")
        assertFalse { a1 == a2 }
        assertFalse { a1.hashCode() == a2.hashCode() }
    }
}