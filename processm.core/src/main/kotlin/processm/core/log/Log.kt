package processm.core.log

import processm.core.log.attribute.Attribute
import java.util.*
import kotlin.collections.HashMap

class Log : XESElement {
    internal val extensionsInternal: MutableMap<String, Extension> = HashMap()
    internal val traceGlobalsInternal: MutableMap<String, Attribute<*>> = HashMap()
    internal val eventGlobalsInternal: MutableMap<String, Attribute<*>> = HashMap()
    internal val traceClassifiersInternal: MutableMap<String, Classifier> = HashMap()
    internal val eventClassifiersInternal: MutableMap<String, Classifier> = HashMap()
    override val attributesInternal: MutableMap<String, Attribute<*>> = HashMap()

    val extensions: Map<String, Extension>
        get() = Collections.unmodifiableMap(extensionsInternal)
    val traceGlobals: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(traceGlobalsInternal)
    val eventGlobals: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(eventGlobalsInternal)
    val traceClassifiers: Map<String, Classifier>
        get() = Collections.unmodifiableMap(traceClassifiersInternal)
    val eventClassifiers: Map<String, Classifier>
        get() = Collections.unmodifiableMap(eventClassifiersInternal)

    var features: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var conceptName: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var identityId: String? = null
        internal set(value) {
            field = value?.intern()
        }
    var lifecycleModel: String? = null
        internal set(value) {
            field = value?.intern()
        }

    val attributes: Map<String, Attribute<*>>
        get() = Collections.unmodifiableMap(attributesInternal)
}