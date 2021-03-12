package processm.core.log

import io.mockk.every
import io.mockk.mockk
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace

object Helpers {
    fun logFromString(text: String): Log =
        Log(
            text.splitToSequence('\n')
                .filter(String::isNotBlank)
                .map { line -> Trace(line.splitToSequence(" ").filter(String::isNotEmpty).map(::event)) }
        )

    fun event(name: String): Event {
        val e = mockk<Event>()
        every { e.conceptName } returns name
        every { e.conceptInstance } returns null
        every { e.lifecycleTransition } returns "complete"
        every { e.hashCode() } returns name.hashCode()
        every { e.toString() } returns name
        return e
    }
}
