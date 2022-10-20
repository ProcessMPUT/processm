package processm.core.log.attribute

import processm.core.log.AttributeMap
import processm.core.log.MutableAttributeMap

/**
 * A dummy null attribute for use wherever the attribute has no value (and type). This may be a case for an expression
 * returned from a PQL [processm.core.querylanguage.Query].
 */
@Deprecated(message="Getting rid of it", level=DeprecationLevel.ERROR)
class NullAttr(key: String, parentStorage: MutableAttributeMap) : Attribute<Any?>(key, parentStorage) {
    override fun getValue(): Any? = null

    override val xesTag: String
        get() = "string" // An arbitrary assigned type to make this attribute serializable

    override fun hashCode(): Int = key.hashCode()

    @Suppress("DEPRECATION_ERROR")
    override fun equals(other: Any?): Boolean = other is NullAttr && other.key == this.key
}
