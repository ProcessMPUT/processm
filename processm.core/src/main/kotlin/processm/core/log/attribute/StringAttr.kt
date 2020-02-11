package processm.core.log.attribute

class StringAttr(key: String, value: String) : Attribute<String>(key) {
    val value: String = value.intern()
    override fun getValue() = this.value
}