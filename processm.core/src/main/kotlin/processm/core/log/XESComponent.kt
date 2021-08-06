package processm.core.log

import processm.core.log.attribute.Attribute
import processm.core.log.attribute.value
import java.util.*

/**
 * XES Element like Log, Trace or Event.
 *
 * Elements store attributes.
 */
abstract class XESComponent {
    /**
     * Standard attribute based on concept:name
     * Standard extension: Concept
     */
    abstract val conceptName: String?

    /**
     * Standard attribute based on identity:id
     * Standard extension: Identity
     */
    abstract val identityId: UUID?

    /**
     * The number of elements (logs, traces, events) represented by this object.
     * This property is designed for use with grouping, where it holds the number of underlying elements. E.g.,
     * for [Trace] variant, it refers to the number of actual traces compliant with this variant.
     * This property is useful for calculating the support for a log/trace/event variant.
     */
    /* Int type should be enough according to the ProcessM grant application, which states that the max total number
     * of business processes is 100 and the max total number of events per process is 10^7. This results in the
     * maximum count of 10^9 ≈ 2^30 for an Event and 10^7 ≈ 2^23 for a Trace and a Log. */
    var count: Int = 1
        internal set

    /**
     * Collection of all attributes associated with this element.
     */
    val attributes: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(attributesInternal)

    /**
     * Shorthand operator for retrieving the value of the attribute of this component.
     * @param attributeName The name of the attribute to retrieve.
     * @return The value of the attribute.
     * @throws IllegalArgumentException if the attribute with the given name does not exist.
     */
    operator fun get(attributeName: String): Any? = requireNotNull(attributesInternal[attributeName]).value

    /**
     * A backing mutable field for [attributes].
     */
    internal val attributesInternal: MutableMap<String, Attribute<*>> = HashMap()

    /**
     * Sets the values of the standard attributes based on the custom attributes and the name map from the standard
     * attribute name into the custom attribute name.
     */
    internal abstract fun setStandardAttributes(nameMap: Map<String, String>)

    /**
     * Sets the values of the custom attributes based on the standard attributes and the name map from the standard
     * attribute name into the custom attribute name. It does not override the values already set for the custom
     * attributes.
     */
    internal abstract fun setCustomAttributes(nameMap: Map<String, String>)

    protected fun <T> setCustomAttribute(
        stdVal: T?,
        stdName: String,
        ctor: (key: String, value: T) -> Attribute<T>,
        nameMap: Map<String, String>
    ) {
        stdVal ?: return
        attributesInternal.computeIfAbsent(nameMap[stdName] ?: stdName) { ctor(it, stdVal) }
    }
}
