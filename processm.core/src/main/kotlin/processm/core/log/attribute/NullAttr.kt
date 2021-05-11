package processm.core.log.attribute

/**
 * A dummy null attribute for use wherever the attribute has no value (and type). This may be a case for an expression
 * returned from a PQL [processm.core.querylanguage.Query].
 */
class NullAttr(key: String) : Attribute<Any?>(key) {
    override fun getValue(): Any? = null

    override val xesTag: String
        get() = throw UnsupportedOperationException("Null attributes are not supported by the IEEE 1849-2016 standard and cannot be serialized.")

    override fun hashCode(): Int = key.hashCode()

    override fun equals(other: Any?): Boolean = other is NullAttr && other.key == this.key
}