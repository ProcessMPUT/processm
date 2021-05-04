package processm.core.log

import processm.core.log.attribute.NullAttr
import processm.core.log.attribute.StringAttr
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity

object Helpers {
    fun logFromString(text: String): Log =
        Log(
            text.splitToSequence('\n')
                .filter(String::isNotBlank)
                .map { line -> Trace(line.splitToSequence(" ").filter(String::isNotEmpty).map(::event)) }
        )

    fun event(name: String): Event = Event().apply {
        attributesInternal["concept:name"] = StringAttr("concept:name", name)
        attributesInternal["lifecycle:transition"] = StringAttr("lifecycle:transition", "complete")
        attributesInternal["concept:instance"] = NullAttr("concept:instance")
        setStandardAttributes(
            mapOf(
                "concept:name" to "concept:name",
                "lifecycle:transition" to "lifecycle:transition",
                "concept:instance" to "concept:instance"
            )
        )
    }

    /**
     * Creates a [Trace] for a given sequence of [Activity]s
     */
    fun trace(vararg activities: Activity): Trace =
        Trace(activities.asList().map { event(it.name) }.asSequence())
}
