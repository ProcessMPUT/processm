package processm.core.log

import processm.core.log.attribute.Attribute

interface XESElement {
    val attributesInternal: MutableMap<String, Attribute<*>>
}