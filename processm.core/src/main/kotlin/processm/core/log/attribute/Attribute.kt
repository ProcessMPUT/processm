package processm.core.log.attribute

import java.util.*
import kotlin.collections.HashMap

abstract class Attribute<T : Any>(key: String) {
    internal val childrenInternal: MutableMap<String, Attribute<*>> = HashMap()
    /**
     * Attribute's key from XES file
     */
    val key: String = key.intern()

    /**
     * Attribute's value
     */
    internal abstract fun getValue(): T

    /**
     * Attributes inside this attribute (nested-attributes)
     * Used as getter based on the internal representation of children
     */
    val children: Map<String, Attribute<*>> = Collections.unmodifiableMap(childrenInternal)
    /**
     * Tag in XES standard
     */
    abstract val xesTag: String
}

val Attribute<*>.value: Any
    get() = this.getValue()
