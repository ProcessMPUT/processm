package processm.core.log

import processm.core.DBTestHelper.dbName
import processm.core.log.attribute.MutableAttributeMap
import processm.core.persistence.connection.DBCache
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

internal class DBXESOutputStreamTest {
    private val content = """<?xml version="1.0" encoding="UTF-8" ?>
        <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
            <extension name="Lifecycle" prefix="lifecycle" uri="http://www.xes-standard.org/lifecycle.xesext"/>
            <extension name="Concept" prefix="conceptowy" uri="http://www.xes-standard.org/concept.xesext"/>
            <extension name="Organizational" prefix="org" uri="http://www.xes-standard.org/org.xesext"/>
            <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
            <global scope="trace">
                <string key="conceptowy:name" value="__INVALID__"/>
            </global>
            <global>
                <string key="lifecycle:transition" value="complete">
                    <string key="meta_3TU:log_type" value="Real-life"/>
                    <list key="listKey">
                        <int key="intInsideListKey" value="22" />
                        <values>
                            <float key="__UNKNOWN__" value="202.617"/>
                            <int key="__NEW__" value="111"/>
                        </values>
                    </list>
                </string>
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
                    <int key="Specialism code" value="61"/>
                    <string key="conceptowy:name" value="1e consult poliklinisch"/>
                    <int key="Activity code" value="410100"/>
                    <date key="time:timestamp" value="2005-01-03T00:00:00.000+01:00"/>
                    <string key="lifecycle:transition" value="complete"/>
                </event>
                <event>
                    <string key="org:group" value="Radiotherapy"/>
                    <int key="Specialism code" value="61"/>
                    <string key="conceptowy:name" value="administratief tarief - eerste pol"/>
                    <int key="Activity code" value="419100"/>
                    <date key="time:timestamp" value="2005-01-03T00:00:00.000+01:00"/>
                    <string key="lifecycle:transition" value="complete"/>
                </event>
            </trace>
        </log>
    """.trimIndent()

    @Test
    fun `Load log into the database`() {
        content.byteInputStream().use { stream ->
            val xesElements = XMLXESInputStream(stream).asSequence()

            DBXESOutputStream(DBCache.get(dbName).getConnection()).use { db ->
                db.write(xesElements)
            }
        }
    }

    /**
     * Demonstrates the error "org.postgresql.util.PSQLException: An I/O error occurred while sending to the backend."
     * when inserting XES file into the database.
     * See #102
     */
    @Test
    @Ignore
    fun `PSQLException An I O error occurred while sending to the backend`() {
        val LOG_FILE = File("../xes-logs/bpi_Challenge_2013_incidents.xes.gz")
        GZIPInputStream(FileInputStream(LOG_FILE)).use { gzip ->
            DBXESOutputStream(DBCache.get(dbName).getConnection()).use { out ->
                out.write(XMLXESInputStream(gzip))
            }
        }
    }

    /**
     * This is an updated version of the code in #162. As expected, after #151 the problem disappeared.
     */
    @Test
    fun `too many range table entries`() {
        fun create(depth: Int, parent: MutableAttributeMap) {
            if (depth == 0) {
                parent["0"] = 0
                return
            }
            val child = parent.children("$depth")
            create(depth - 1, child.children(0))
            create(depth - 1, child.children(1))
        }

        val depth = 14
        val attributes = MutableAttributeMap().apply { create(depth, this) }
        assertEquals(1 shl depth, attributes.flatView.size)
        val log = processm.core.log.hierarchical.Log(
            emptySequence(),
            attributesInternal = attributes
        )
        DBXESOutputStream(DBCache.get(dbName).getConnection()).use { output ->
            output.write(log)
        }
    }
}
