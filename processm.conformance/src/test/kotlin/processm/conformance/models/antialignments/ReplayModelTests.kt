package processm.conformance.models.antialignments

import processm.core.models.commons.Activity
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ReplayModelTests {
    @Test
    fun `replay a b c`() {
        val a = Act("a")
        val b = Act("b")
        val c = Act("c")
        val model = ReplayModel(listOf(a, b, c))
        val instance = model.createInstance()

        assertContentEquals(sequenceOf(a), instance.availableActivities)
        assertEquals(instance.availableActivityExecutions.first(), instance.getExecutionFor(a))
        instance.availableActivityExecutions.first().execute()

        assertContentEquals(sequenceOf(b), instance.availableActivities)
        assertEquals(instance.availableActivityExecutions.first(), instance.getExecutionFor(b))
        instance.availableActivityExecutions.first().execute()

        assertContentEquals(sequenceOf(c), instance.availableActivities)
        assertEquals(instance.availableActivityExecutions.first(), instance.getExecutionFor(c))
        instance.availableActivityExecutions.first().execute()

        assertContentEquals(emptySequence(), instance.availableActivities)
    }

    private class Act(override val name: String) : Activity {
        override fun equals(other: Any?): Boolean = other is Act && name == other.name

        override fun hashCode(): Int = name.hashCode()
    }
}
