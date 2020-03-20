package processm.core.log.attribute

/**
 * String attribute
 *
 * Tag inside XES file: <string>
 */
class StringAttr(key: String, val value: String) : Attribute<String>(key) {
    override fun getValue() = this.value
    override val xesTag: String
        get() = "string"

    override fun valueToString(): String {
        return this.value
    }
}