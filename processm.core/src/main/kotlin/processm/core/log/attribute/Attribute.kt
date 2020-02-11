package processm.core.log.attribute

abstract class Attribute<T : Comparable<T>>(key: String) {
    val key: String = key.intern()
    internal abstract fun getValue(): T
}

val Attribute<*>.value: Comparable<*>
    get() = this.getValue()
