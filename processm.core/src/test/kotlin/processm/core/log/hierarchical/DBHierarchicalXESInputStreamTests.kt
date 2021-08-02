package processm.core.log.hierarchical

import processm.core.DBTestHelper.dbName
import processm.core.helpers.hierarchicalCompare
import processm.core.log.DBXESOutputStream
import processm.core.log.Event
import processm.core.log.XESInputStream
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DBHierarchicalXESInputStreamTests {
    val log: Sequence<Log> =
        sequenceOf(Log(sequenceOf(
            Trace(sequenceOf(
                Event().apply { conceptName = "A"; setCustomAttributes(emptyMap()) },
                Event().apply { conceptName = "B"; setCustomAttributes(emptyMap()) },
                Event().apply { conceptName = "C"; setCustomAttributes(emptyMap()) }
            )).apply {
                conceptName = "T"
                setCustomAttributes(emptyMap())
            },
            Trace(
                sequenceOf(
                    Event().apply { conceptName = "D"; setCustomAttributes(emptyMap()) },
                    Event().apply { conceptName = "E"; setCustomAttributes(emptyMap()) },
                    Event().apply { conceptName = "F"; setCustomAttributes(emptyMap()) }
                )).apply {
                conceptName = "U"
                setCustomAttributes(emptyMap())
            }
        )).apply {
            conceptName = "L"
            setCustomAttributes(emptyMap())
        })
    private val logId: Int by lazyOf(setUp())

    private fun setUp(): Int {
        DBXESOutputStream(DBCache.get(dbName).getConnection()).use {
            it.write(log.toFlatSequence())
        }

        DBCache.get(dbName).getConnection().use {
            val response = it.prepareStatement("""SELECT id FROM logs ORDER BY id DESC LIMIT 1""").executeQuery()
            response.next()

            return response.getInt("id")
        }
    }

    @Test
    fun castTest() {
        val fromDB = DBHierarchicalXESInputStream(dbName, Query(logId))
        var implicitCast: XESInputStream = fromDB
        assertNotNull(implicitCast)

        implicitCast = fromDB.toFlatSequence()
        assertNotNull(implicitCast)
    }

    @Test
    fun repeatableReadTest() {
        val fromDB = DBHierarchicalXESInputStream(dbName, Query(logId))
        assertTrue(hierarchicalCompare(log, fromDB))
        assertTrue(hierarchicalCompare(log, fromDB))
    }

    @Test
    fun repeatableInterleavedReadTest() {
        val fromDB = DBHierarchicalXESInputStream(dbName, Query(logId))
        assertTrue(hierarchicalCompare(fromDB, fromDB))
        assertTrue(hierarchicalCompare(fromDB, fromDB))
    }

    @Test
    fun phantomReadTest() {
        var traceId: Long = -1L
        try {
            val fromDB = DBHierarchicalXESInputStream(dbName, Query(logId))
            assertTrue(hierarchicalCompare(log, fromDB))

            // insert phantom event Z into trace T
            // TODO("Replace with API call when issue #49 is complete")
            DBCache.get(dbName).getConnection().use { conn ->
                conn.autoCommit = false
                traceId = conn.prepareStatement(
                    """
                    SELECT id 
                    FROM traces 
                    WHERE log_id=? and "concept:name" = 'T'
                    ORDER BY id LIMIT 1
                    """.trimIndent()
                ).apply {
                    setInt(1, logId)
                }.executeQuery().use {
                    it.next()
                    it.getLong(1)
                }

                conn.prepareStatement("INSERT INTO events(trace_id, \"concept:name\") VALUES (?, ?)").apply {
                    setLong(1, traceId)
                    setString(2, "Z")
                }.execute()
                conn.commit()
            }

            // implementation should ensure that phantom reads do not occur
            assertTrue(hierarchicalCompare(log, fromDB))
            // but a new sequence should reflect changes
            val fromDB2 = DBHierarchicalXESInputStream(dbName, Query(logId))
            assertFalse(hierarchicalCompare(log, fromDB2))
        } finally {
            // Delete log with id $logId
            // TODO("Replace with API call when issue #49 is complete")
            DBCache.get(dbName).getConnection().use { conn ->
                conn.prepareStatement("DELETE FROM events WHERE trace_id=? AND \"concept:name\"='Z'").apply {
                    setLong(1, traceId)
                }.execute()
            }
        }
    }
}
