package processm.core.log.attribute

import processm.core.log.AttributeMap

/**
 * String attribute
 *
 * Tag inside XES file: <string>
 */
class StringAttr(key: String, val value: String, parentStorage: AttributeMap<Attribute<*>>) :
    Attribute<String>(key, parentStorage) {
    override fun getValue(): String = this.value
    override val xesTag: String
        get() = "string"

    override fun valueToString(): String {
        return this.value
    }
}