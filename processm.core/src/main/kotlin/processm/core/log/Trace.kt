package processm.core.log

import processm.core.log.attribute.Attribute
import java.util.*

class Trace : XESElement {
    override val attributesInternal: MutableMap<String, Attribute<*>> = TreeMap(String.CASE_INSENSITIVE_ORDER)

    var conceptName: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var costCurrency: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var costTotal: Double? = null
        internal set
    var identityId: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var isEventStream: Boolean = false
        internal set

    val attributes: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(attributesInternal)
}