package processm.core.log.attribute

/**
 * A dummy null attribute for use wherever the attribute has no value (and type). This may be a case for an expression
 * returned from a PQL [processm.core.querylanguage.Query].
 */
class NullAttr(key: String) : Attribute<Any?>(key) {
    override fun getValue(): Any? = null

    override val xesTag: String
        get() = "string" // An arbitrary assigned type to make this attribute serializable

    override fun hashCode(): Int = key.hashCode()

    override fun equals(other: Any?): Boolean = other is NullAttr && other.key == this.key
}
