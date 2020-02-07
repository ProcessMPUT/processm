package processm.core.log.attribute

class StringAttr(key: String, private val value: String) : Attribute<String>(key) {
    override fun getValue() = this.value.intern()
}