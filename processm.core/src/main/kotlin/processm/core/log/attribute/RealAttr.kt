package processm.core.log.attribute

import processm.core.log.AttributeMap
import processm.core.log.MutableAttributeMap

/**
 * Real number attribute
 *
 * Tag inside XES file: <float>
 */
@Deprecated(message="Getting rid of it", level=DeprecationLevel.ERROR)
class RealAttr(key: String, val value: Double, parentStorage: MutableAttributeMap) :
    Attribute<Double>(key, parentStorage) {
    override fun getValue(): Double = this.value
    override val xesTag: String
        get() = "float"

    override fun valueToString(): String {
        return this.value.toString()
    }
}