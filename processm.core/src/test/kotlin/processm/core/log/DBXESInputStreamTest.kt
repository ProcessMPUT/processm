package processm.core.log

import org.junit.jupiter.api.Tag
import processm.core.DBTestHelper
import processm.core.DBTestHelper.dbName
import processm.core.log.attribute.Attribute.IDENTITY_ID
import processm.core.log.attribute.Attribute.LIFECYCLE_TRANSITION
import processm.core.log.attribute.Attribute.ORG_GROUP
import processm.core.log.attribute.Attribute.TIME_TIMESTAMP
import processm.core.log.attribute.AttributeMap
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.log.hierarchical.toFlatSequence
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.helpers.time.parseISO8601
import java.io.File
import java.util.*
import kotlin.test.*

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

            assertEquals(getValue("conceptowy:name"), "__INVALID__")
            assertEquals(getValue("meta_org:resource_events_standard_deviation"), 2.2)
            assertEquals(getValue("extras"), 0.617)
        }

        with(receivedLog.traceGlobals.children("meta_org:resource_events_standard_deviation")) {
            assertEquals(size, 2)

            assertEquals(getValue("UNKNOWN"), 202.617)
            assertEquals(getValue("meta:key"), 22L)

            with(children("meta:key")) {
                assertEquals(size, 7)

                assertEquals(getValue(ORG_GROUP), "Radiotherapy")
                assertEquals(getValue("Specialism code"), 61L)
                assertEquals(getValue("conceptowy:name"), "1e consult poliklinisch")
                assertEquals(getValue("Activity code"), 410100L)
                assertEquals("2005-01-03T00:00:00.000+01:00".parseISO8601(), getValue(TIME_TIMESTAMP))
                assertEquals(getValue(LIFECYCLE_TRANSITION), "complete")

                with(children("listKey")) {
                    assertEquals(size, 1)
                    assertEquals(getValue("intInsideListKey"), 12L)
                }
                with(children("listKey").asList()) {
                    assertEquals(this.size, 2)
                    assertIs<AttributeMap>(this[0])
                    assertIs<AttributeMap>(this[1])

                    with(this[0] as AttributeMap) {
                        assertEquals(1, size)
                        assertEquals(6.17, this["__UNKNOWN__"])
                    }

                    with(this[1] as AttributeMap) {
                        assertEquals(1, size)
                        assertEquals(111L, this["__NEW__"])
                    }
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

            assertEquals(getValue("conceptowy:name"), "__INVALID__")
            assertEquals(getValue(ORG_GROUP), "__INVALID__")
        }
    }

    @Test
    fun `Log contains attributes`() {
        val stream = DBXESInputStream(dbName, Query(logId)).iterator()

        val receivedLog = stream.next() as Log

        with(receivedLog.attributes) {
            assertEquals(size, 4)

            assertEquals(getValue("meta_org:resource_events_standard_deviation"), 202.617)
            assertEquals(getValue("meta_3TU:log_type"), "Real-life")
            assertEquals(getValue("conceptowy:name"), "Some amazing log file")
            assertEquals(getValue("meta_org:role_events_total"), 150291L)

            with(children("meta_org:resource_events_standard_deviation")) {
                assertEquals(size, 1)
                assertEquals(getValue("UNKNOWN"), 202.617)
            }

            with(children("meta_org:role_events_total")) {
                assertEquals(size, 1)
                assertEquals(getValue("UNKNOWN"), 150291L)
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

            assertEquals("Radiotherapy", getValue(ORG_GROUP))
            assertEquals(61L, getValue("Specialism code"))
            assertEquals(1, children("Specialism code").size)
            assertEquals(20.20, children("Specialism code").getValue("fl-y"))
            assertEquals("administratief tarief - eerste pol", getValue("conceptowy:name"))
            assertEquals("complete", getValue(LIFECYCLE_TRANSITION))
            assertEquals(419100L, getValue("Activity code"))
            assertEquals(date, getValue(TIME_TIMESTAMP))
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

    @Ignore("This test is slow and `reading a log with over 65536 traces all at once` should test exactly the same thing")
    @Test
    @Tag("slow")
    fun `too many parameters while reading Hospital_Billing-Event_Log from DB`() {
        val uuid = DBTestHelper.loadLog(File("../xes-logs/Hospital_Billing-Event_Log.xes.gz"))
        DBXESInputStream(dbName, Query("where l:id=$uuid")).count()
    }

    @Tag("slow")
    @Test
    fun `reading a log with over 65536 traces all at once`() {
        val traces = List(65537) { processm.core.log.hierarchical.Trace(sequenceOf(Event())) }
        val log = processm.core.log.hierarchical.Log(traces.asSequence())
        val uuid = UUID.randomUUID()
        log.identityId = uuid
        DBXESOutputStream(DBCache.get(dbName).getConnection()).use { output ->
            output.write(log.toFlatSequence())
        }
        val actual = DBXESInputStream(dbName, Query("where l:id=$uuid")).count()
        assertEquals(2 * 65537 + 1, actual) // log + 65537 traces, each with a single event
    }

    private fun setUp(): Int {
        loadIntoDB()

        DBCache.get(dbName).getConnection().use {
            val response = it.prepareStatement("""SELECT id FROM logs ORDER BY id DESC LIMIT 1""").executeQuery()
            response.next()

            return response.getInt("id")
        }
    }

    @Ignore("This is an interesting case which does not work. Left here as an example.")
    @Test
    fun `querying for a trace with two events, each satisfying one condition`() {
        val logId = UUID.randomUUID()
        val tracesId = (1..10).map { UUID.randomUUID() }
        val eventsId = (1..10).map { UUID.randomUUID() }
        DBXESOutputStream(DBCache.get(dbName).getConnection()).use { output ->
            output.write(
                sequenceOf(
                    Log(mutableAttributeMapOf(IDENTITY_ID to logId)),
                    Trace(mutableAttributeMapOf(IDENTITY_ID to tracesId[0])),
                    Event(mutableAttributeMapOf(IDENTITY_ID to eventsId[0], "a" to "1")),
                    Event(mutableAttributeMapOf(IDENTITY_ID to eventsId[1], "a" to "2")),
                    Trace(mutableAttributeMapOf(IDENTITY_ID to tracesId[1])),
                    Event(mutableAttributeMapOf(IDENTITY_ID to eventsId[2], "a" to "3")),
                    Event(mutableAttributeMapOf(IDENTITY_ID to eventsId[3], "a" to "2")),
                )
            )
        }
        val query = "select t:identity:id where l:identity:id = $logId and [^e:a] = '2' and [^e:a] = '1'"
        val result = DBXESInputStream(dbName, Query(query)).filterIsInstance<Trace>().toList()
        assertEquals(1, result.size)
        assertEquals(tracesId[0], result[0].identityId)
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
