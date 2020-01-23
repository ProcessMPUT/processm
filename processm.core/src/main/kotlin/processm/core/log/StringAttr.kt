package processm.core.log

class StringAttr(key: String, value: String) {
    val key: String = key.intern()
    val value: String = value.intern()
}
