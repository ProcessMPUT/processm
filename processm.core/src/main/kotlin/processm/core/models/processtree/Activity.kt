package processm.core.models.processtree

open class Activity(name: String, val isSpecial: Boolean = false) : Node() {
    /**
     * Activity name
     */
    val name: String = name.intern()

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Activity) return false
        return name == other.name && isSpecial == other.isSpecial && super.equals(other)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isSpecial.hashCode()
        return result
    }
}