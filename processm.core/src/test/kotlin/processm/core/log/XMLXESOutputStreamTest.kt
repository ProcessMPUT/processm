package processm.core.log

import processm.core.helpers.hierarchicalCompare
import processm.core.log.hierarchical.DatabaseHierarchicalXESInputStream
import processm.core.log.hierarchical.toFlatSequence
import processm.core.persistence.DBConnectionPool
import java.io.StringWriter
import javax.xml.stream.XMLOutputFactory
import kotlin.test.*


internal class XMLXESOutputStreamTest {
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
    """.trimIndent().byteInputStream()

    @Test
    fun `Abort will close the stream and user can close it again without any exception`() {
        val log = Log().also {
            it.features = "nested-attributes"
        }

        val received = StringWriter()
        val writer = XMLXESOutputStream(XMLOutputFactory.newInstance().createXMLStreamWriter(received))

        writer.use {
            it.write(log)
            it.abort()
        }
    }

    @Test
    fun `Close XML without passing any XML Element`() {
        val received = StringWriter()
        val writer = XMLXESOutputStream(XMLOutputFactory.newInstance().createXMLStreamWriter(received))

        writer.close()
        assertEquals(received.toString(), "")
    }

    @Test
    fun `Write log without any events and attributes`() {
        val log = Log().also {
            it.features = "nested-attributes"
        }

        val received = StringWriter()
        val writer = XMLXESOutputStream(XMLOutputFactory.newInstance().createXMLStreamWriter(received))

        writer.write(log)
        writer.close()

        val output = XMLXESInputStream(received.toString().byteInputStream()).iterator()

        with(output.next() as Log) {
            assertEquals(features, "nested-attributes")
        }

        assertFalse(output.hasNext())
    }

    @Test
    fun `Event stream as input - ignore trace element`() {
        val log = Log()
        val traceEventStream = Trace().also {
            it.isEventStream = true
        }
        val event = Event()

        val received = StringWriter()
        val writer = XMLXESOutputStream(XMLOutputFactory.newInstance().createXMLStreamWriter(received))

        writer.write(log)
        writer.write(traceEventStream)
        writer.write(event)
        writer.close()

        val output = XMLXESInputStream(received.toString().byteInputStream()).iterator()

        with(output.next() as Log) {
            assertNull(features)
        }

        with(output.next() as Event) {
            assertEquals(attributes.size, 0)
        }

        assertFalse(output.hasNext())
    }

    @Test
    fun `Compare imported and exported XML files`() {
        storeLog(XMLXESInputStream(content).asSequence())
        val firstlogId = getLastLogId()
        val fromDB = DatabaseHierarchicalXESInputStream(firstlogId)

        // Log to XML file
        val received = StringWriter()
        val writer = XMLXESOutputStream(XMLOutputFactory.newInstance().createXMLStreamWriter(received))
        writer.write(fromDB.toFlatSequence())
        writer.close()

        // Store XML
        storeLog(XMLXESInputStream(received.toString().byteInputStream()).asSequence())

        val logFromDB = DatabaseHierarchicalXESInputStream(firstlogId)
        val logFromXML = DatabaseHierarchicalXESInputStream(getLastLogId())

        assertTrue(hierarchicalCompare(logFromDB, logFromXML))
    }


    private fun storeLog(sequence: Sequence<XESElement>) {
        DatabaseXESOutputStream().use {
            it.write(sequence)
        }
    }

    private fun getLastLogId(): Int {
        DBConnectionPool.getConnection().use {
            val response = it.prepareStatement("""SELECT id FROM logs ORDER BY id DESC LIMIT 1""").executeQuery()
            response.next()

            return response.getInt("id")
        }
    }
}