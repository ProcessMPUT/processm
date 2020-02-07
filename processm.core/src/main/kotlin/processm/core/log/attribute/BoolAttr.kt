package processm.core.log.attribute

class BoolAttr(key: String, val value: Boolean) : Attribute<Boolean>(key) {
    override fun getValue() = this.value
}
