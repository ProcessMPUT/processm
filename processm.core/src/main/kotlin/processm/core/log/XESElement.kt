package processm.core.log

import processm.core.log.attribute.Attribute

/**
 * XES Element like Log, Trace or Event
 *
 * Inside element we expect to store attributes.
 */
interface XESElement {
    val attributesInternal: MutableMap<String, Attribute<*>>
}