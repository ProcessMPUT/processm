package processm.core.log

import processm.core.DBTestHelper.dbName
import processm.core.helpers.parseISO8601
import processm.core.log.attribute.Attribute.Companion.LIFECYCLE_TRANSITION
import processm.core.log.attribute.Attribute.Companion.ORG_GROUP
import processm.core.log.attribute.Attribute.Companion.TIME_TIMESTAMP
import processm.core.log.attribute.ListAttr
import processm.core.log.attribute.value
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DBXESInputStreamTest {
    private val content: String = """<?xml version="1.0" encoding="UTF-8" ?>
        <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
            <extension name="Lifecycle" prefix="lifecycle" uri="http://www.xes-standard.org/lifecycle.xesext"/>
            <extension name="Concept" prefix="conceptowy" uri="http://www.xes-standard.org/concept.xesext"/>
            <extension name="Organizational" prefix="org" uri="http://www.xes-standard.org/org.xesext"/>
            <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
            <global scope="trace">
                <string key="conceptowy:name" value="__INVALID__"/>
                <float key="meta_org:resource_events_standard_deviation" value="2.2">
                    <float key="UNKNOWN" value="202.617"/>
                    <int key="meta:key" value="22">
                        <string key="org:group" value="Radiotherapy"/>
                        <list key="listKey">
                            <int key="intInsideListKey" value="12" />
                            <values>
                                <float key="__UNKNOWN__" value="6.17"/>
                                <int key="__NEW__" value="111"/>
                            </values>
                        </list>
                        <int key="Specialism code" value="61"/>
                        <string key="conceptowy:name" value="1e consult poliklinisch"/>
                        <int key="Activity code" value="410100"/>
                        <date key="time:timestamp" value="2005-01-03T00:00:00.000+01:00"/>
                        <string key="lifecycle:transition" value="complete"/>
                    </int>
                </float>
                <float key="extras" value="0.617"/>
            </global>
            <global>
                <string key="conceptowy:name" value="__INVALID__"/>
                <string key="org:group" value="__INVALID__"/>
            </global>
            <classifier name="Event Name" keys="conceptowy:name"/>
            <classifier scope="trace" name="Department Classifier" keys="org:group"/>
            <float key="meta_org:resource_events_standard_deviation" value="202.617">
                <float key="UNKNOWN" value="202.617"/>
            </float>
            <string key="meta_3TU:log_type" value="Real-life"/>
            <string key="conceptowy:name" value="Some amazing log file"/>
            <int key="meta_org:role_events_total" value="150291">
                <int key="UNKNOWN" value="150291"/>
            </int>
            <event>
                <string key="org:group" value="Radiotherapy"/>
                <int key="Specialism code" value="61">
                    <float key="fl-y" value="20.20"/>
                </int>
                <string key="conceptowy:name" value="administratief tarief - eerste pol"/>
                <int key="Activity code" value="419100"/>
                <date key="time:timestamp" value="2005-01-03T00:00:00.000+01:00"/>
                <string key="lifecycle:transition" value="complete"/>
            </event>
        </log>
    """.trimIndent()
    private val logId by lazyOf(setUp())

    @Test
    fun `Receive expected extensions in Log structure`() {
        val stream = DBXESInputStream(dbName, Query(logId)).iterator()

        val receivedLog = stream.next() as Log

        assertEquals(receivedLog.extensions.size, 4)

        assertEquals(receivedLog.extensions.getValue("org").name, "Organizational")
        assertEquals(receivedLog.extensions.getValue("org").prefix, "org")

        assertEquals(receivedLog.extensions.getValue("conceptowy").name, "Concept")
        assertEquals(receivedLog.extensions.getValue("conceptowy").prefix, "conceptowy")

        assertEquals(receivedLog.extensions.getValue("lifecycle").name, "Lifecycle")
        assertEquals(receivedLog.extensions.getValue("lifecycle").prefix, "lifecycle")

        assertEquals(receivedLog.extensions.getValue("time").name, "Time")
        assertEquals(receivedLog.extensions.getValue("time").prefix, "time")
    }

    @Test
    fun `Receive expected classifiers in Log structure`() {
        val stream = DBXESInputStream(dbName, Query(logId)).iterator()

        val receivedLog = stream.next() as Log

        assertEquals(receivedLog.eventClassifiers.size, 1)
        assertEquals(receivedLog.traceClassifiers.size, 1)

        assertEquals(receivedLog.eventClassifiers.getValue("Event Name").name, "Event Name")
        assertEquals(receivedLog.eventClassifiers.getValue("Event Name").keys, "conceptowy:name")

        assertEquals(receivedLog.traceClassifiers.getValue("Department Classifier").name, "Department Classifier")
        assertEquals(receivedLog.traceClassifiers.getValue("Department Classifier").keys, ORG_GROUP)
    }

    @Test
    fun `Log contains named, special values in structure`() {
        val stream = DBXESInputStream(dbName, Query(logId)).iterator()

        val receivedLog = stream.next() as Log

        assertEquals(receivedLog.xesFeatures, "nested-attributes")
        assertEquals(receivedLog.conceptName, "Some amazing log file")
        assertEquals(receivedLog.identityId, null)
        assertEquals(receivedLog.lifecycleModel, null)
    }

    @Test
    fun `Log contains trace global attributes`() {
        val stream = DBXESInputStream(dbName, Query(logId)).iterator()

        val receivedLog = stream.next() as Log

        with(receivedLog.traceGlobals) {
            assertEquals(size, 3)

            assertEquals(getValue("conceptowy:name").value, "__INVALID__")
            assertEquals(getValue("meta_org:resource_events_standard_deviation").value, 2.2)
            assertEquals(getValue("extras").value, 0.617)
        }

        with(receivedLog.traceGlobals.getValue("meta_org:resource_events_standard_deviation")) {
            assertEquals(children.size, 2)

            assertEquals(children.getValue("UNKNOWN").value, 202.617)
            assertEquals(children.getValue("meta:key").value, 22L)

            with(children.getValue("meta:key")) {
                assertEquals(children.size, 7)

                assertEquals(children.getValue(ORG_GROUP).value, "Radiotherapy")
                assertEquals(children.getValue("Specialism code").value, 61L)
                assertEquals(children.getValue("conceptowy:name").value, "1e consult poliklinisch")
                assertEquals(children.getValue("Activity code").value, 410100L)
                assertEquals("2005-01-03T00:00:00.000+01:00".parseISO8601(), children.getValue(TIME_TIMESTAMP).value)
                assertEquals(children.getValue(LIFECYCLE_TRANSITION).value, "complete")

                with(children.getValue("listKey") as ListAttr) {
                    assertEquals(children.size, 1)
                    assertEquals(value.size, 2)

                    assertEquals(children.getValue("intInsideListKey").value, 12L)

                    assertEquals(value[0].value, 6.17)
                    assertEquals(value[0].key, "__UNKNOWN__")

                    assertEquals(value[1].value, 111L)
                    assertEquals(value[1].key, "__NEW__")
                }
            }
        }
    }

    @Test
    fun `Log contains event global attributes`() {
        val stream = DBXESInputStream(dbName, Query(logId)).iterator()

        val receivedLog = stream.next() as Log

        with(receivedLog.eventGlobals) {
            assertEquals(size, 2)

            assertEquals(getValue("conceptowy:name").value, "__INVALID__")
            assertEquals(getValue(ORG_GROUP).value, "__INVALID__")
        }
    }

    @Test
    fun `Log contains attributes`() {
        val stream = DBXESInputStream(dbName, Query(logId)).iterator()

        val receivedLog = stream.next() as Log

        with(receivedLog.attributes) {
            assertEquals(size, 4)

            assertEquals(getValue("meta_org:resource_events_standard_deviation").value, 202.617)
            assertEquals(getValue("meta_3TU:log_type").value, "Real-life")
            assertEquals(getValue("conceptowy:name").value, "Some amazing log file")
            assertEquals(getValue("meta_org:role_events_total").value, 150291L)

            with(getValue("meta_org:resource_events_standard_deviation").children) {
                assertEquals(size, 1)
                assertEquals(getValue("UNKNOWN").value, 202.617)
            }

            with(getValue("meta_org:role_events_total").children) {
                assertEquals(size, 1)
                assertEquals(getValue("UNKNOWN").value, 150291L)
            }
        }
    }

    @Test
    fun `Event stream - special Trace element received`() {
        val stream = DBXESInputStream(dbName, Query(logId)).iterator()

        // Ignore Log element
        assert(stream.next() is Log)

        val receivedTrace = stream.next() as Trace

        with(receivedTrace) {
            assertTrue(isEventStream)
        }
    }

    @Test
    fun `Receive events from the DB`() {
        val stream = DBXESInputStream(dbName, Query(logId)).iterator()

        // Ignore Log element
        assertTrue(stream.next() is Log)

        // Ignore Trace element
        assertTrue(stream.next() is Trace)

        val receivedEvent = stream.next() as Event
        val date = "2005-01-03T00:00:00.000+01:00".parseISO8601()

        with(receivedEvent.attributes) {
            assertEquals(size, 6)

            assertEquals("Radiotherapy", getValue(ORG_GROUP).value)
            assertEquals(61L, getValue("Specialism code").value)
            assertEquals(1, getValue("Specialism code").children.size)
            assertEquals(20.20, getValue("Specialism code").children.getValue("fl-y").value)
            assertEquals("administratief tarief - eerste pol", getValue("conceptowy:name").value)
            assertEquals("complete", getValue(LIFECYCLE_TRANSITION).value)
            assertEquals(419100L, getValue("Activity code").value)
            assertEquals(date, getValue(TIME_TIMESTAMP).value)
        }

        with(receivedEvent) {
            assertEquals(conceptName, "administratief tarief - eerste pol")
            assertEquals(lifecycleTransition, "complete")
            assertTrue(date.compareTo(timeTimestamp) == 0)
            assertEquals(orgGroup, "Radiotherapy")
        }

        assertFalse(stream.hasNext())
    }

    @Test
    fun `No elements in sequence when log not found`() {
        val missingLogId = -1
        val stream = DBXESInputStream(dbName, Query(missingLogId)).iterator()

        assertFalse(stream.hasNext())
    }

    private fun setUp(): Int {
        loadIntoDB()

        DBCache.get(dbName).getConnection().use {
            val response = it.prepareStatement("""SELECT id FROM logs ORDER BY id DESC LIMIT 1""").executeQuery()
            response.next()

            return response.getInt("id")
        }
    }

    private fun loadIntoDB() {
        content.byteInputStream().use { stream ->
            val xesElements = XMLXESInputStream(stream).asSequence()

            DBXESOutputStream(DBCache.get(dbName).getConnection()).use { db ->
                db.write(xesElements)
            }
        }
    }
}
