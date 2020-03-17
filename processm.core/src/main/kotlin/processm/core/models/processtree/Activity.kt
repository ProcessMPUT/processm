package processm.core.models.processtree

open class Activity(name: String) : Node() {
    /**
     * Activity name
     */
    val name: String = name.intern()

    /**
     * Name of activity as representation of object
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