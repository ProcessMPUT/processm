package processm.core.log.attribute

class RealAttr(key: String, val value: Double) : Attribute<Double>(key) {
    override fun getValue() = this.value
}