package processm.core.log.attribute

/**
 * Real number attribute
 *
 * Tag inside XES file: <float>
 */
class RealAttr(key: String, val value: Double) : Attribute<Double>(key) {
    override fun getValue() = this.value
    override val xesTag: String
        get() = "float"

    override fun valueToString(): String {
        return this.value.toString()
    }
}