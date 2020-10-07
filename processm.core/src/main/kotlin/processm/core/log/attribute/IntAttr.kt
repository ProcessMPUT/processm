package processm.core.log.attribute

/**
 * Integer number attribute
 *
 * Tag inside XES file: <int>
 */
class IntAttr(key: String, val value: Long) : Attribute<Long>(key) {
    override fun getValue(): Long = this.value
    override val xesTag: String
        get() = "int"

    override fun valueToString(): String {
        return this.value.toString()
    }
}