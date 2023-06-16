package processm.services.helpers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.OutputStream
import kotlin.reflect.full.findAnnotation


/**
 * Annotation specifying the name of an event represented by the annotated class in the context of Server-Sent Events
 */
@Target(AnnotationTarget.CLASS)
annotation class ServerSentEvent(val eventName: String)

/**
 * Replace each occurrence of `\` in [input] with `\\`, and `\n` with `\\n`
 */
internal fun escapeNewLine(input: String): String = input.replace("\\", "\\\\").replace("\n", "\\n")


/**
 * A stream representing server-sent events. Create using [ApplicationCall.eventStream]
 *
 * @see https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/
 */
class EventStream(val base: OutputStream) {

    /**
     * Write an event named [event] with Json-serialized [data] as the `data` field. [data] must be [Serializable]
     */
    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> writeEvent(event: String, data: T) {
        with(base) {
            write("event:".toByteArray())
            write(event.toByteArray())
            write("\ndata:".toByteArray())
            Json.encodeToStream(data, this)
            write("\n\n".toByteArray())
            flush()
        }
    }

    /**
     * Write an event to the stream using [ServerSentEvent] annotation on [T] as the event name and serializing [data] as JSON
     */
    inline fun <reified T> writeEvent(data: T) {
        val eventName =
            requireNotNull(T::class.findAnnotation<ServerSentEvent>()?.eventName) { "Annotate the class with @ServerSentEvent or use the `writeEvent` variant with explicitly-specified event name" }
        writeEvent(eventName, data)
    }

    /**
     * Write arbitrary event to the stream. Backslash `\` is escaped as `\\` and newline `\n` as `\\n`.
     */
    fun writeEvent(event: String, data: String) {
        val eventEscaped = escapeNewLine(event)
        val dataEscaped = escapeNewLine(data)
        val text = "event:$eventEscaped\ndata:$dataEscaped\n\n"
        base.write(text.toByteArray())
        base.flush()
    }
}

/**
 * Create [EventStream] for the current request.
 *
 * The stream disables the Compression plugin, as it seems to buffer the data despite calling to flush
 */
suspend fun ApplicationCall.eventStream(callback: suspend EventStream.() -> Unit) {
    attributes.put(SuppressionAttribute, true)
    respondOutputStream(ContentType.Text.EventStream, HttpStatusCode.OK) {
        EventStream(this).callback()
    }
}