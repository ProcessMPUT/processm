package processm.core.log.attribute

import processm.core.log.AttributeMap

/**
 * Integer number attribute
 *
 * Tag inside XES file: <int>
 */
@Deprecated(message="Getting rid of it", level=DeprecationLevel.ERROR)
class IntAttr(key: String, val value: Long, parentStorage: AttributeMap) :
    Attribute<Long>(key, parentStorage) {
    override fun getValue(): Long = this.value
    override val xesTag: String
        get() = "int"

    override fun valueToString(): String {
        return this.value.toString()
    }
}