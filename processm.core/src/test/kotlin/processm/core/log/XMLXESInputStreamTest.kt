package processm.core.log

import processm.core.helpers.parseISO8601
import processm.core.log.attribute.Attribute.LIFECYCLE_TRANSITION
import processm.core.log.attribute.Attribute.ORG_GROUP
import processm.core.log.attribute.Attribute.TIME_TIMESTAMP
import processm.core.log.attribute.AttributeMap
import kotlin.test.*

internal class XMLXESInputStreamTest {
    private val content = """<?xml version="1.0" encoding="UTF-8" ?>
        <!-- OpenXES library version: 1.0RC7 -->
        <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
            <extension name="Lifecycle" prefix="lifecycle" uri="http://www.xes-standard.org/lifecycle.xesext"/>
            <extension name="Concept" prefix="conceptowy" uri="http://www.xes-standard.org/concept.xesext"/>
            <extension name="Organizational" prefix="org" uri="http://www.xes-standard.org/org.xesext"/>
            <extension name="Metadata_Organizational" prefix="meta_org" uri="http://www.xes-standard.org/meta_org.xesext"/>
            <global scope="trace">
                <string key="conceptowy:name" value="__INVALID__"/>
            </global>
            <global>
                <string key="lifecycle:transition" value="complete"/>
                <string key="conceptowy:name" value="__INVALID__"/>
                <string key="org:group" value="__INVALID__"/>
                <date key="time:timestamp" value="1970-01-01T01:00:00.000+01:00"/>
                <list key="globalListKey">
                    <int key="intInsideListKey" value="25" />
                    <values>
                        <float key="__UNKNOWN__" value="123.617"/>
                        <int key="__NEW__" value="456"/>
                    </values>
                </list>
            </global>
            <classifier name="Event Name" keys="conceptowy:name"/>
            <classifier scope="trace" name="Department Classifier" keys="org:group"/>
            <float key="meta_org:resource_events_standard_deviation" value="202.617">
                <float key="UNKNOWN" value="202.617"/>
            </float>
            <list key="listKey">
                <int key="intInsideListKey" value="22" />
                <values>
                    <float key="__UNKNOWN__" value="202.617"/>
                    <int key="__NEW__" value="111"/>
                </values>
            </list>
            <id key="id" value="22a66e06-9371-4dbf-aee3-b58b44564a0c"/>
            <string key="meta_3TU:log_type" value="Real-life"/>
            <string key="conceptowy:name" value="Some amazing log file"/>
            <int key="meta_org:role_events_total" value="150291">
                <int key="UNKNOWN" value="150291"/>
            </int>
            <trace>
                <date key="End date" value="2006-01-04T23:45:36.000+01:00"/>
                <int key="Age" value="33"/>
                <string key="conceptowy:name" value="00000001"/>
                <event>
                    <string key="org:group" value="Radiotherapy"/>
                    <int key="Number of executions" value="1"/>
                    <int key="Specialism code" value="61"/>
                    <string key="conceptowy:name" value="1e consult poliklinisch"/>
                    <string key="Producer code" value="SRTH"/>
                    <string key="Section" value="Section 5"/>
                    <int key="Activity code" value="410100"/>
                    <date key="time:timestamp" value="2005-01-03T00:00:00+01:00"/>
                    <string key="lifecycle:transition" value="complete"/>
                </event>
                <event>
                    <string key="org:group" value="Radiotherapy"/>
                    <int key="Number of executions" value="1"/>
                    <int key="Specialism code" value="61"/>
                    <string key="conceptowy:name" value="administratief tarief - eerste pol"/>
                    <string key="Producer code" value="SRTH"/>
                    <string key="Section" value="Section 5"/>
                    <int key="Activity code" value="419100"/>
                    <date key="time:timestamp" value="2005-01-03T00:00:00+01:00"/>
                    <string key="lifecycle:transition" value="complete"/>
                </event>
            </trace>
        </log>
    """.trimIndent()

    @Test
    fun `XES parser can recognize extensions and prepare it inside Log`() {
        content.byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            val conceptExtension = Extension("Concept", "conceptowy", "http://www.xes-standard.org/concept.xesext")
            val lifecycleExtension = Extension("Lifecycle", "lifecycle", "http://www.xes-standard.org/lifecycle.xesext")
            val organizationalExtension = Extension("Organizational", "org", "http://www.xes-standard.org/org.xesext")
            val metadataOrganizationalExtension =
                Extension("Metadata_Organizational", "meta_org", "http://www.xes-standard.org/meta_org.xesext")

            val receivedLog: Log = iterator.next() as Log

            assertEquals(receivedLog.extensions.size, 4)

            assertEquals(receivedLog.extensions.getValue("org").name, "Organizational")
            assertEquals(receivedLog.extensions.getValue("org").prefix, "org")
            assertSame(receivedLog.extensions.getValue("org").extension, organizationalExtension.extension)

            assertEquals(receivedLog.extensions.getValue("conceptowy").name, "Concept")
            assertEquals(receivedLog.extensions.getValue("conceptowy").prefix, "conceptowy")
            assertSame(receivedLog.extensions.getValue("conceptowy").extension, conceptExtension.extension)

            assertEquals(receivedLog.extensions.getValue("lifecycle").name, "Lifecycle")
            assertEquals(receivedLog.extensions.getValue("lifecycle").prefix, "lifecycle")
            assertSame(receivedLog.extensions.getValue("lifecycle").extension, lifecycleExtension.extension)

            assertEquals(receivedLog.extensions.getValue("meta_org").name, "Metadata_Organizational")
            assertEquals(receivedLog.extensions.getValue("meta_org").prefix, "meta_org")
            assertSame(receivedLog.extensions.getValue("meta_org").extension, metadataOrganizationalExtension.extension)
        }
    }

    @Test
    fun `XES parser is able to load trace globals`() {
        content.byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            val receivedLog: Log = iterator.next() as Log

            assertEquals(receivedLog.traceGlobals.size, 1)
            assertEquals(receivedLog.traceGlobals.getValue("conceptowy:name"), "__INVALID__")
        }
    }

    @Test
    fun `XES parser is able to load event globals even when scope key missing`() {
        content.byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            val receivedLog: Log = iterator.next() as Log

            assertEquals(receivedLog.eventGlobals.size, 5)

            assertEquals(receivedLog.eventGlobals.getValue("conceptowy:name"), "__INVALID__")
            assertEquals(receivedLog.eventGlobals.getValue(LIFECYCLE_TRANSITION), "complete")
            assertEquals(receivedLog.eventGlobals.getValue(ORG_GROUP), "__INVALID__")
            assertEquals(
                receivedLog.eventGlobals.getValue(TIME_TIMESTAMP),
                "1970-01-01T01:00:00.000+01:00".parseISO8601()
            )
            with(receivedLog.eventGlobals.children("globalListKey").asList() as List<AttributeMap>) {
                val children = receivedLog.eventGlobals.children("globalListKey")
                assertEquals(1, children.size)
                assertEquals(25L, children["intInsideListKey"])
                assertEquals(2, size)
                with(this[0]) {
                    assertIs<AttributeMap>(this)
                    assertEquals(123.617, this["__UNKNOWN__"])
                }
                with(this[1]) {
                    assertIs<AttributeMap>(this)
                    assertEquals(456L, this["__NEW__"])
                }
            }
        }
    }

    @Test
    fun `XES parser is able to load classifiers into log structure`() {
        content.byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            val receivedLog: Log = iterator.next() as Log

            assertEquals(receivedLog.eventClassifiers.size, 1)
            assertEquals(receivedLog.traceClassifiers.size, 1)

            assertEquals(receivedLog.eventClassifiers.getValue("Event Name").name, "Event Name")
            assertEquals(receivedLog.eventClassifiers.getValue("Event Name").keys, "conceptowy:name")

            assertEquals(receivedLog.traceClassifiers.getValue("Department Classifier").name, "Department Classifier")
            assertEquals(receivedLog.traceClassifiers.getValue("Department Classifier").keys, ORG_GROUP)
        }
    }

    @Test
    fun `XES parser will throw exception when found invalid XML tag inside log structure`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <invalid-tag name="Lifecycle" prefix="lifecycle" uri="http://www.xes-standard.org/lifecycle.xesext"/>
            </log>
        """.trimIndent().byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            val thrown = assertFailsWith<Exception> {
                iterator.next()
            }

            assertEquals(thrown.message, "Found unexpected XML tag: invalid-tag in line 3 column 118")
        }
    }

    @Test
    fun `XES parser will throw exception when found invalid XML tag inside trace structure`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <trace>
                    <foo key="bar" value="123.22"/>
                </trace>
            </log>
        """.trimIndent().byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            // Skip Log structure
            assert(iterator.next() is Log)

            val thrown = assertFailsWith<Exception> {
                iterator.next()
            }

            assertEquals(thrown.message, "Found unexpected XML tag: foo in line 4 column 52")
        }
    }

    @Test
    fun `XES parser will throw exception when found invalid global scope`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <global scope="invalid-scope">
                    <string key="conceptowy:name" value="__INVALID__"/>
                </global>
            </log>
        """.trimIndent().byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            val thrown = assertFailsWith<Exception> {
                iterator.next()
            }

            assertEquals(
                thrown.message,
                "Illegal <global> scope. Expected 'trace' or 'event', found invalid-scope in line 3 column 47"
            )
        }
    }

    @Test
    fun `XES parser will throw exception when found invalid classifier's scope`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <classifier scope="invalid" name="invalid" keys="concept:name"/>
            </log>
        """.trimIndent().byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            val thrown = assertFailsWith<Exception> {
                iterator.next()
            }

            assertEquals(
                thrown.message,
                "Illegal <classifier> scope. Expected 'trace' or 'event', found invalid in line 3 column 81"
            )
        }
    }

    @Test
    fun `XES parser is able to recognize list attribute`() {
        content.byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            val receivedLog: Log = iterator.next() as Log
            val listAttr = receivedLog.attributes.children("listKey").asList()

            assertEquals(22L, receivedLog.attributes.children("listKey").getValue("intInsideListKey"))

            assertEquals(202.617, listAttr[0]["__UNKNOWN__"])

            assertEquals(111L, listAttr[1]["__NEW__"])
        }
    }

    @Test
    fun `XES parser is able to build trace structure`() {
        content.byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            // Skip Log structure
            assert(iterator.next() is Log)

            val receivedTrace: Trace = iterator.next() as Trace

            assertEquals(receivedTrace.attributes.size, 3)

            assertEquals(
                receivedTrace.attributes.getValue("End date"),
                "2006-01-04T23:45:36.000+01:00".parseISO8601()
            )
            assertEquals(receivedTrace.attributes.getValue("Age"), 33L)
            assertEquals(receivedTrace.attributes.getValue("conceptowy:name"), "00000001")
        }
    }

    @Test
    fun `XES parser is able to build event structure`() {
        content.byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            // Skip Log structure
            assert(iterator.next() is Log)

            // Skip Trace structure
            assert(iterator.next() is Trace)

            val receivedEvent: Event = iterator.next() as Event

            assertEquals(receivedEvent.attributes.size, 9)

            assertEquals(
                receivedEvent.attributes.getValue(TIME_TIMESTAMP),
                "2005-01-03T00:00:00+01:00".parseISO8601()
            )
            assertEquals(receivedEvent.attributes.getValue("Activity code"), 410100L)
            assertEquals(receivedEvent.attributes.getValue(LIFECYCLE_TRANSITION), "complete")
            assertEquals(receivedEvent.attributes.getValue("Section"), "Section 5")
            assertEquals(receivedEvent.attributes.getValue("Producer code"), "SRTH")
            assertEquals(receivedEvent.attributes.getValue("conceptowy:name"), "1e consult poliklinisch")
            assertEquals(receivedEvent.attributes.getValue(ORG_GROUP), "Radiotherapy")
            assertEquals(receivedEvent.attributes.getValue("Number of executions"), 1L)
            assertEquals(receivedEvent.attributes.getValue("Specialism code"), 61L)
        }
    }

    @Test
    fun `XES parser is able to add meaning assigned to most popular extensions' fields inside log structure`() {
        content.byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            val receivedLog: Log = iterator.next() as Log

            assertEquals(receivedLog.conceptName, "Some amazing log file")
            assertEquals(receivedLog.identityId, null)
            assertEquals(receivedLog.lifecycleModel, null)
        }
    }

    @Test
    fun `XES parser is able to add meaning assigned to most popular extensions' fields inside trace structure`() {
        content.byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            // Skip Log structure
            assert(iterator.next() is Log)

            val receivedTrace: Trace = iterator.next() as Trace

            assertEquals(receivedTrace.conceptName, "00000001")
            assertEquals(receivedTrace.costCurrency, null)
            assertEquals(receivedTrace.costTotal, null)
            assertEquals(receivedTrace.identityId, null)
            assertEquals(receivedTrace.isEventStream, false)
        }
    }

    @Test
    fun `XES parser is able to add meaning assigned to most popular extensions' fields inside event structure`() {
        content.byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()

            // Skip Log structure
            assert(iterator.next() is Log)

            // Skip Trace structure
            assert(iterator.next() is Trace)

            val receivedEvent: Event = iterator.next() as Event

            assertEquals(receivedEvent.conceptName, "1e consult poliklinisch")
            assertEquals(receivedEvent.lifecycleTransition, "complete")
            assertEquals(receivedEvent.orgGroup, "Radiotherapy")
        }
    }
}
