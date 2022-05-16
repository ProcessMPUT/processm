package processm.conformance.rca

import kotlin.reflect.KClass

/**
 * A feature (=column) to be used in a propositional dataset.
 *
 * @param name The column name
 * @param datatype Tye column datatype
 * @param default The default value to be used in case of a missing value
 */
open class Feature(open val name: String, open val datatype: KClass<*>, open val default: Any? = if(datatype == Boolean::class) false else null)

/**
 * A distinguished feature denoting the classification label
 */
val Label = Feature("label", Boolean::class)

/**
 * A feature denoting the value of the attribute [attribute] for the [occurrence]-th occurrence of the event with the concept name [eventName]
 */
data class EventFeature(
    val eventName: String?,
    val occurrence: Int,
    val attribute: String,
    override val datatype: KClass<*>
) : Feature("$eventName occurrence $occurrence's $attribute", datatype)

/**
 * A feature denoting that there is at least one event with the concept name [eventName] and with the attribute [attribute] of the value [value]
 *
 * The default value is `false` - if it was not explicitly set, then there's no such event.
 */
data class AnyNamedEventFeature(
    val eventName: String?,
    val attribute: String,
    val value: Any
) : Feature("Any event $eventName's $attribute=$value", Boolean::class) {
    override val default: Boolean = false
}

/**
 * A feature denoting that there is at least one event with the attribute [attribute] of the value [value]
 *
 * The default value is `false` - if it was not explicitly set, then there's no such event.
 */
data class AnyEventFeature(
    val attribute: String,
    val value: Any
) : Feature("Any event's $attribute=$value", Boolean::class) {
    override val default: Boolean = false
}
