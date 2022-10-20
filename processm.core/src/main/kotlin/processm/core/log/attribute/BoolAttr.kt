package processm.core.log.attribute

import processm.core.log.AttributeMap
import processm.core.log.MutableAttributeMap

/**
 * Boolean attribute
 *
 * Tag inside XES file: <boolean>
 */
@Deprecated(message="Getting rid of it", level=DeprecationLevel.ERROR)
class BoolAttr(key: String, val value: Boolean, parentStorage: MutableAttributeMap) :
    Attribute<Boolean>(key, parentStorage) {
    override fun getValue(): Boolean = this.value
    override val xesTag: String
        get() = "boolean"

    override fun valueToString(): String {
        return this.value.toString()
    }
}
