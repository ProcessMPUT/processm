package processm.conformance.models.antialignments

import processm.core.models.commons.Activity
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ReplayModelTests {

    companion object {
        private val a = Act("a")
        private val b = Act("b")
        private val c = Act("c")
        private val model = ReplayModel(listOf(a, b, c))
    }


    @Test
    fun `replay a b c`() {
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

    @Test
    fun `start activity is a`() {
        assertContentEquals(sequenceOf(a), model.startActivities)
    }

    @Test
    fun `end activity is c`() {
        assertContentEquals(sequenceOf(c), model.endActivities)
    }

    @Test
    fun `no decision points`() {
        assertEquals(0, model.decisionPoints.count())
    }

    @Test
    fun `no control structures`() {
        assertEquals(0, model.controlStructures.count())
    }

    private class Act(override val name: String) : Activity {
        override fun equals(other: Any?): Boolean = other is Act && name == other.name

        override fun hashCode(): Int = name.hashCode()
    }
}
