package processm.core.log.attribute

class IntAttr(key: String, val value: Long) : Attribute<Long>(key) {
    override fun getValue() = this.value
}