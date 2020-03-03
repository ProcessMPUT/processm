package processm.core.log

/**
 * A classifier assigns an identity to each event that makes it comparable to others (via their assigned identity).
 *
 * Can be declared on <trace> or <event> level.
 * Default: <event>
 */
class Classifier(name: String?, keys: String?) {
    /**
     * The name of the classifier
     */
    val name: String? = name?.intern()
    /**
     * The white-space-separated list of attribute keys that constitute this classifier.
     * These attributes shall be declared global at the proper (either event or trace) level.
     */
    val keys: String? = keys?.intern()

    /**
     * Classifiers are equal if both contain the same `name` and `keys`
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Classifier) return false
        return name == other.name && keys == other.keys
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (keys?.hashCode() ?: 0)
        return result
    }
}