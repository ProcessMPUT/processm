package processm.core.log.attribute

import processm.core.log.AttributeMap
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass

/**
 * The base class for the attribute compliant with the XES standard.
 */
abstract class Attribute<T>(key: String, parentStorage:AttributeMap<Attribute<*>>) {

    companion object {
        /**
         * Stores a generally understood name for a log/trace/event.
         */
        const val CONCEPT_NAME = "concept:name"

        /**
         * This represents an identifier of the activity instance whose execution has generated the event.
         */
        const val CONCEPT_INSTANCE = "concept:instance"

        /**
         * The value contains the cost amount for a cost driver.
         */
        const val COST_AMOUNT = "cost:amount"

        /**
         * The currency (using the ISO 4217:2008 standard) of all costs of this trace/event.
         */
        const val COST_CURRENCY = "cost:currency"

        /**
         *  The value contains the id for the cost driver.
         */
        const val COST_DRIVER = "cost:driver"

        /**
         * A detailed list containing cost driver details.
         */
        const val COST_DRIVERS = "cost:drivers"

        /**
         * Total cost incurred for a trace/event. The value represents the sum of all the cost amounts within the element.
         */
        const val COST_TOTAL = "cost:total"

        /**
         * The value contains the cost type (e.g., Fixed, Overhead, Materials).
         */
        const val COST_TYPE = "cost:type"

        /**
         * The database identifier of an object.
         */
        const val DB_ID = "db:id"

        /**
         * Unique identifier (UUID) for the log/trace/event.
         */
        const val IDENTITY_ID = "identity:id"

        /**
         * This attribute refers to the lifecycle transactional model used for all events in the log. If this
         * attribute has a value of "standard", the standard lifecycle transactional model of this extension is
         * assumed. If it is has a value of "bpaf", the Business Process Analytics Format (BPAF) lifecycle transactional
         * model is assumed.
         */
        const val LIFECYCLE_MODEL = "lifecycle:model"

        /**
         * The transition attribute is defined for events, and specifies the lifecycle transition of each event.
         */
        const val LIFECYCLE_TRANSITION = "lifecycle:transition"

        /**
         * The state attribute is defined for events, and specifies the lifecycle state of each event.
         */
        const val LIFECYCLE_STATE = "lifecycle:state"

        /**
         * The group within the organizational structure, of which the resource that triggered the event is a member.
         */
        const val ORG_GROUP = "org:group"

        /**
         * The name, or identifier, of the resource that triggered the event.
         */
        const val ORG_RESOURCE = "org:resource"

        /**
         * The role of the resource that triggered the event, within the organizational structure.
         */
        const val ORG_ROLE = "org:role"

        /**
         * The UTC time at which the event occurred.
         */
        const val TIME_TIMESTAMP = "time:timestamp"

        /**
         * The version of XES standard.
         */
        const val XES_VERSION = "xes:version"

        /**
         * The features used in the holding log.
         */
        const val XES_FEATURES = "xes:features"
    }

    //TODO Czy obiekt ma sie sam rejestrowac w storage czy to jest tylko childrenInternal?
    //TODO Ale jeżeli nie będzie się sam rejestrował to jak zapewnić poprawność konstrukcji obiektów?
    //TODO Jeżeli będzie, to i tak - jak zapewnić poprawność? Może jakoś inaczej dodawać dzieci? Albo je wytwarzać inaczej?
    //TODO Co z listami? Jak one maja byc obslugiwane? Miec odrebny storage?

    val childrenInternal: AttributeMap<Attribute<*>> = parentStorage.children(key)

    /**
     * Gets the child attribute. This is a shortcut call equivalent to `children.get(key)`.
     */
    operator fun get(key: String): Attribute<*>? = childrenInternal[key]


    /**
     * Sets the child attribute
     */
    internal operator fun set(key: String, child: Attribute<*>) {
        require(child.childrenInternal == childrenInternal.children(key))
        childrenInternal[key] = child
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
