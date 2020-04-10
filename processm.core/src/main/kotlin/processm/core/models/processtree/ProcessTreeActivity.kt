package processm.core.models.processtree

import processm.core.models.commons.Activity
import processm.core.models.processtree.execution.ActivityExecution
import processm.core.models.processtree.execution.ExecutionNode

open class ProcessTreeActivity(name: String) : Node(), Activity {
    /**
     * The name of an activity as a representation of an object
     */
    override val name: String = name.intern()

    /**
     * The symbol of an activity - for activity this will be name
     */
    override val symbol: String
        get() = name

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is ProcessTreeActivity) return false
        return name == other.name && super.equals(other)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    override val startActivities: kotlin.sequences.Sequence<ProcessTreeActivity> = sequenceOf(this)

    override val endActivities: kotlin.sequences.Sequence<ProcessTreeActivity> = sequenceOf(this)

    override fun executionNode(parent: ExecutionNode?): ActivityExecution = ActivityExecution(this, parent)
}