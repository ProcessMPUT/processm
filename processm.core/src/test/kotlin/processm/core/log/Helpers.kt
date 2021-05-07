package processm.core.log

import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace

object Helpers {
    fun logFromString(text: String): Log =
        Log(
            text.splitToSequence('\n')
                .filter(String::isNotBlank)
                .map { line -> Trace(line.splitToSequence(" ").filter(String::isNotEmpty).map(::event)) }
        )

    fun event(name: String): Event = Event().apply {
        conceptName = name
        conceptInstance = null
        lifecycleTransition = "complete"
    }
}
