package processm.core.log.attribute

import java.util.*
import kotlin.collections.HashMap

/**
 * The base class for the attribute compliant with the XES standard.
 */
abstract class Attribute<T>(key: String) {
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
     * Value to String formatting
     */
    internal open fun valueToString(): String = getValue().toString()

    /**
     * Attributes inside this attribute (nested-attributes)
     * Used as getter based on the internal representation of children
     */
    val children: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(childrenInternal)

    /**
     * Tag in XES standard
     */
    abstract val xesTag: String

    /**
     * Equals if are the same or contains the same `value`, `key`, `xesTag` and children (first level - no deep equals)
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Attribute<*>
        return value == other.value && childrenInternal == other.childrenInternal
                && key == other.key && xesTag == other.xesTag
    }

    /**
     * Deep equals - should be equals AND each attribute in children also the same
     */
    fun deepEquals(other: Attribute<*>?): Boolean {
        return this == other && this.childrenInternal.size == other.childrenInternal.size && this.childrenInternal.all {
            it.value.deepEquals(other.childrenInternal[it.key])
        }
    }

    override fun hashCode(): Int {
        var result = childrenInternal.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + xesTag.hashCode()
        return result
    }
}

val Attribute<*>.value: Any?
    get() = this.getValue()

fun Map<String, Attribute<*>>.deepEquals(other: Map<String, Attribute<*>>): Boolean =
    this == other && this.all { it.value.deepEquals(other[it.key]) }
