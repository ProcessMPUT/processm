package processm.core.log.attribute

import processm.core.log.AttributeMap
import processm.core.log.MutableAttributeMap
import java.util.*

/**
 * ID attribute
 *
 * Valid values for an ID attribute are values that conform to the ID datatype
 * all string representations of universally unique identifiers (UUIDs).
 *
 * Tag inside XES file: <id>
 */
@Deprecated(message="Getting rid of it", level=DeprecationLevel.ERROR)
class IDAttr(key: String, val value: UUID, parentStorage: MutableAttributeMap) :
    Attribute<UUID>(key, parentStorage) {
    override fun getValue() = this.value
    override val xesTag: String
        get() = "id"

    override fun valueToString(): String {
        return this.value.toString()
    }
}