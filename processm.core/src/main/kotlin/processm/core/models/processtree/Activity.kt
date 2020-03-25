package processm.core.models.processtree

open class Activity(name: String) : Node() {
    /**
     * The name of an activity as a representation of an object
     */
    val name: String = name.intern()

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
}