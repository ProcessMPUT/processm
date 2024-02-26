package processm.core.log

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import processm.core.log.Helpers.event
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventSerializerTest {

    @Test
    fun `encode and decode empty event`() {
        val event = Event()
        val json = Json.encodeToString(event)
        assertEquals("{}", json)

        val decoded = Json.decodeFromString<Event>(json)
        assertEquals(null, decoded.conceptName)
    }

    @Test
    fun `encode and decode simple event with only concept_name attribute`() {
        val event = event("Ragnarök")
        val json = Json.encodeToString(event)
        assertTrue("\"concept:name\":\"Ragnarök\"" in json, json)

        val decoded = Json.decodeFromString<Event>(json)
        assertEquals(event.conceptName, decoded.conceptName)
    }

    @Test
    fun `encode and decode an event with concept_name and time_timestamp attributes`() {
        val event = event("Ragnarök", "time:timestamp" to Instant.now())
        val json = Json.encodeToString(event)
        assertTrue("\"concept:name\":\"Ragnarök\"" in json, json)
        assertTrue("\"time:timestamp\":\"${event.timeTimestamp}\"" in json, json)

        val decoded = Json.decodeFromString<Event>(json)
        assertEquals(event.conceptName, decoded.conceptName)
        assertEquals(event.timeTimestamp, decoded.timeTimestamp)
        assertEquals(event.timeTimestamp, decoded["time:timestamp"])
    }


    @Test
    fun `encode and decode an event with concept_name and cost_total attributes`() {
        val event = event("Ragnarök", "cost:total" to 3.14)
        val json = Json.encodeToString(event)
        assertTrue("\"concept:name\":\"Ragnarök\"" in json, json)
        assertTrue("\"cost:total\":3.14" in json, json)

        val decoded = Json.decodeFromString<Event>(json)
        assertEquals(event.conceptName, decoded.conceptName)
        assertEquals(event.costTotal, decoded.costTotal)
        assertEquals(event.costTotal, decoded["cost:total"])
    }
}
