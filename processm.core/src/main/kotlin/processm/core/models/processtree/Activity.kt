package processm.core.models.processtree

open class Activity(name: String) : Node() {
    /**
     * Activity name
     */
    val name: String = name.intern()
}