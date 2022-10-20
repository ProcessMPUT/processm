package processm.core.log.attribute

import processm.core.log.AttributeMap
import processm.core.log.MutableAttributeMap

/**
 * String attribute
 *
 * Tag inside XES file: <string>
 */
@Deprecated(message="Getting rid of it", level=DeprecationLevel.ERROR)
class StringAttr(key: String, val value: String, parentStorage: MutableAttributeMap) :
    Attribute<String>(key, parentStorage) {
    override fun getValue(): String = this.value
    override val xesTag: String
        get() = "string"

    override fun valueToString(): String {
        return this.value
    }
}