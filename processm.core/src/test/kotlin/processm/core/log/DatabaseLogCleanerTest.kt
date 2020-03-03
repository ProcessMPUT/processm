package processm.core.log

import processm.core.persistence.DBConnectionPool
import kotlin.test.*

internal class DatabaseLogCleanerTest {
    @BeforeTest
    fun `Insert log`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
        <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
            <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
            <global scope="trace">
                <string key="conceptowy:name" value="__INVALID__"/>
            </global>
            <classifier scope="trace" name="Department Classifier" keys="org:group"/>
            <float key="meta_org:resource_events_standard_deviation" value="202.617">
                <float key="UNKNOWN" value="202.617"/>
            </float>
            <string key="meta_3TU:log_type" value="Real-life"/>
            <trace>
                <string key="conceptowy:name" value="00000001"/>
                <event>
                    <string key="lifecycle:transition" value="complete"/>
                </event>
                <event>
                    <string key="lifecycle:transition" value="complete"/>
                </event>
            </trace>
        </log>
    """.trimIndent().byteInputStream().use { stream ->
            DatabaseXESOutputStream().use { db ->
                db.write(XMLXESInputStream(stream))
            }
        }
    }

    @Test
    fun `Remove whole log from the DB`() {
        val logId = getLastLogId()
        val before = setStateBefore()
        assertTrue(DatabaseXESInputStream(logId).iterator().hasNext())

        DatabaseLogCleaner.removeLog(logId)

        assertFalse(DatabaseXESInputStream(logId).iterator().hasNext())

        assertEquals(countRecordsInTable("logs"), before["logs"]!! - 1)
        assertEquals(countRecordsInTable("traces"), before["traces"]!! - 1)
        assertEquals(countRecordsInTable("events"), before["events"]!! - 2)
        assertEquals(countRecordsInTable("globals"), before["globals"]!! - 1)
        assertEquals(countRecordsInTable("classifiers"), before["classifiers"]!! - 1)
        assertEquals(countRecordsInTable("extensions"), before["extensions"]!! - 1)
        assertEquals(countRecordsInTable("logs_attributes"), before["logs_attributes"]!! - 3)
        assertEquals(countRecordsInTable("traces_attributes"), before["traces_attributes"]!! - 1)
        assertEquals(countRecordsInTable("events_attributes"), before["events_attributes"]!! - 2)
    }

    private fun setStateBefore(): HashMap<String, Int> {
        return HashMap<String, Int>().also {
            it["logs"] = countRecordsInTable("logs")
            it["traces"] = countRecordsInTable("traces")
            it["globals"] = countRecordsInTable("globals")
            it["events"] = countRecordsInTable("events")
            it["classifiers"] = countRecordsInTable("classifiers")
            it["extensions"] = countRecordsInTable("extensions")
            it["logs_attributes"] = countRecordsInTable("logs_attributes")
            it["traces_attributes"] = countRecordsInTable("traces_attributes")
            it["events_attributes"] = countRecordsInTable("events_attributes")
        }
    }

    private fun countRecordsInTable(table: String): Int {
        return DBConnectionPool.getConnection().use {
            val response = it.prepareStatement("SELECT COUNT(id) AS count FROM $table").executeQuery()
            response.next()
            response.getInt("count")
        }
    }

    private fun getLastLogId(): Int {
        return DBConnectionPool.getConnection().use {
            val response = it.prepareStatement("""SELECT id FROM logs ORDER BY id DESC LIMIT 1""").executeQuery()
            response.next()
            response.getInt("id")
        }
    }
}