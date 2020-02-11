package processm.core.log

import processm.core.log.attribute.Attribute
import java.util.*
import kotlin.collections.HashMap

class Event : XESElement {
    override val attributesInternal: MutableMap<String, Attribute<*>> = HashMap()

    var conceptName: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var conceptInstance: String? = null
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
    var lifecycleTransition: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var lifecycleState: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var orgResource: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var orgRole: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var orgGroup: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var timeTimestamp: Date? = null
        internal set

    val attributes: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(attributesInternal)
}