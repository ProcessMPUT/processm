package processm.core.log

import processm.core.Brand.dbName
import processm.core.persistence.connection.DBCache
import kotlin.test.Test

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
}