package processm.core.log.attribute

/**
 * Boolean attribute
 *
 * Tag inside XES file: <boolean>
 */
class BoolAttr(key: String, val value: Boolean) : Attribute<Boolean>(key) {
    override fun getValue(): Boolean = this.value
    override val xesTag: String
        get() = "boolean"

    override fun valueToString(): String {
        return this.value.toString()
    }
}
