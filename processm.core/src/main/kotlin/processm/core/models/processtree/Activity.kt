package processm.core.models.processtree

import processm.core.models.commons.AbstractActivity

open class Activity(name: String) : Node(), AbstractActivity {
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
        if (other !is Activity) return false
        return name == other.name && super.equals(other)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    override val startActivities: kotlin.sequences.Sequence<Activity> = sequenceOf(this)

    override val endActivities: kotlin.sequences.Sequence<Activity> = sequenceOf(this)
}