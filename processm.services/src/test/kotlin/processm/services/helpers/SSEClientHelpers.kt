package processm.services.helpers

import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import processm.services.api.ComponentUpdateEventPayload
import java.util.*
import kotlin.test.assertEquals

data class SSE(val eventName: String?, val data: String)


/**
 * An unsound and incomplete parser of server-sent events
 *
 * @see https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation
 */
suspend fun ByteReadChannel.readSSE(): SSE {
    var eventName: String? = null
    val data = StringBuilder()
    while (true) {
        // This is sloppy, as readUTF8Line treats both \n and \r\n as line terminators, thus possibly leading to misinterpreting received data.
        // It doesn't seem to be a problem in the current use case and, nevertheless, it is recommended to encode the content of the event as JSON
        val line = readUTF8Line()
        if (line.isNullOrEmpty())
            break
        val i = line.indexOf(':')
        if (i <= 0)
            continue    // Ignore, even though the spec says something else
        val key = line.substring(0, i)
        var value = line.substring(i + 1)
        if (value[0] == ' ') value = value.substring(1)
        when (key) {
            "event" -> eventName = value
            "data" -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(value)
            }

            else -> error("Unknown field `$key'")
        }
    }
    return SSE(eventName, data.toString())
}


fun SSE.asUpdateEvent(): UUID {
    assertEquals("update", eventName)
    return Json.decodeFromString<ComponentUpdateEventPayload>(data).componentId
}