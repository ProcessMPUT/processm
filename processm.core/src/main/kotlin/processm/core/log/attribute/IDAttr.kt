package processm.core.log.attribute

/**
 * ID attribute
 *
 * Valid values for an ID attribute are values that conform to the ID datatype
 * all string representations of universally unique identifiers (UUIDs).
 *
 * Tag inside XES file: <id>
 */
class IDAttr(key: String, val value: String) : Attribute<String>(key) {
    override fun getValue(): String = this.value
    override val xesTag: String
        get() = "id"

    override fun valueToString(): String {
        return this.value
    }
}