package processm.core.log.attribute

import java.time.Instant
import java.util.*
import kotlin.reflect.KClass

/**
 * The base class for the attribute compliant with the XES standard.
 */
abstract class Attribute<T>(key: String) {
    private var childrenInternal: MutableMap<String, Attribute<*>>? = null

    /**
     * Gets the child attribute. This is a shortcut call equivalent to `children.get(key)`.
     */
    operator fun get(key: String): Attribute<*>? = childrenInternal?.get(key)


    /**
     * Sets the child attribute
     */
    internal operator fun set(key: String, child: Attribute<*>) {
        if (childrenInternal === null)
            childrenInternal = HashMap()
        childrenInternal!![key] = child
    }

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
        get() = Collections.unmodifiableMap(childrenInternal ?: emptyMap())

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
        return this == other && this.childrenInternal?.size == other.childrenInternal?.size && this.childrenInternal?.all {
            it.value.deepEquals(other.childrenInternal?.get(it.key))
        } ?: true
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

/**
 * Returns `KClass` corresponding to the value of the attribute
 */
val Attribute<*>.valueType: KClass<*>
    get() = when (this) {
        is BoolAttr -> Boolean::class
        is DateTimeAttr -> Instant::class
        is IDAttr -> UUID::class
        is IntAttr -> Long::class
        is ListAttr -> List::class
        is NullAttr -> Nothing::class
        is RealAttr -> Double::class
        is StringAttr -> String::class
        else -> TODO("Type ${this::class} is not supported")
    }
