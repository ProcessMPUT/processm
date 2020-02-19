package processm.core.log.attribute

class IDAttr(key: String, value: String) : Attribute<String>(key) {
    val value: String = value.intern()
    override fun getValue() = this.value.intern()
}