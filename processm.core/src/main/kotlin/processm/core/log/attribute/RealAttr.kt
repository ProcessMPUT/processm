package processm.core.log.attribute

import processm.core.log.AttributeMap

/**
 * Real number attribute
 *
 * Tag inside XES file: <float>
 */
class RealAttr(key: String, val value: Double, parentStorage: AttributeMap<Attribute<*>>) :
    Attribute<Double>(key, parentStorage) {
    override fun getValue(): Double = this.value
    override val xesTag: String
        get() = "float"

    override fun valueToString(): String {
        return this.value.toString()
    }
}