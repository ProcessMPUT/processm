package processm.core.log.attribute

import java.util.*
import kotlin.collections.HashMap

abstract class Attribute<T : Any>(key: String) {
    internal val childrenInternal: MutableMap<String, Attribute<*>> = HashMap()

    val key: String = key.intern()
    internal abstract fun getValue(): T

    val children: Map<String, Attribute<*>> = Collections.unmodifiableMap(childrenInternal)
}

val Attribute<*>.value: Any
    get() = this.getValue()
