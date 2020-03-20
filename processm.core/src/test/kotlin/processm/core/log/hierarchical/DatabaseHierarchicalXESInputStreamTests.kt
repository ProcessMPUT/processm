package processm.core.log.hierarchical

import processm.core.helpers.hierarchicalCompare
import processm.core.log.DatabaseXESOutputStream
import processm.core.log.Event
import processm.core.log.XESInputStream
import processm.core.persistence.DBConnectionPool
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseHierarchicalXESInputStreamTests {
    val log: Sequence<Log> =
        sequenceOf(Log(sequenceOf(
            Trace(sequenceOf(
                Event().apply { conceptName = "A" },
                Event().apply { conceptName = "B" },
                Event().apply { conceptName = "C" }
            )).apply {
                conceptName = "T"
            },
            Trace(sequenceOf(
                Event().apply { conceptName = "D" },
                Event().apply { conceptName = "E" },
                Event().apply { conceptName = "F" }
            )).apply {
                conceptName = "U"
            }
        )).apply {
            conceptName = "L"
        })
    private val logId: Int by lazyOf(setUp())

    private fun setUp(): Int {
        DatabaseXESOutputStream().use {
            it.write(log.toFlatSequence())
        }

        DBConnectionPool.getConnection().use {
            val response = it.prepareStatement("""SELECT id FROM logs ORDER BY id DESC LIMIT 1""").executeQuery()
            response.next()

            return response.getInt("id")
        }
    }

    @Test
    fun castTest() {
        val fromDB = DatabaseHierarchicalXESInputStream(logId)
        var implicitCast: XESInputStream = fromDB
        assertNotNull(implicitCast)

        implicitCast = fromDB.toFlatSequence()
        assertNotNull(implicitCast)
    }

    @Test
    fun repeatableReadTest() {
        val fromDB = DatabaseHierarchicalXESInputStream(logId)
        assertTrue(hierarchicalCompare(log, fromDB))
        assertTrue(hierarchicalCompare(log, fromDB))
    }

    @Test
    fun repeatableInterleavedReadTest() {
        val fromDB = DatabaseHierarchicalXESInputStream(logId)
        assertTrue(hierarchicalCompare(fromDB, fromDB))
        assertTrue(hierarchicalCompare(fromDB, fromDB))
    }

    @Test
    fun phantomReadTest() {
        var traceId: Long = -1L
        try {
            val fromDB = DatabaseHierarchicalXESInputStream(logId)
            assertTrue(hierarchicalCompare(log, fromDB))

            // insert phantom event Z into trace T
            // TODO("Replace with API call when issue #49 is complete")
            DBConnectionPool.getConnection().use { conn ->
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
            val fromDB2 = DatabaseHierarchicalXESInputStream(logId)
            assertFalse(hierarchicalCompare(log, fromDB2))
        } finally {
            // Delete log with id $logId
            // TODO("Replace with API call when issue #49 is complete")
            DBConnectionPool.getConnection().use { conn ->
                conn.prepareStatement("DELETE FROM events WHERE trace_id=? AND \"concept:name\"='Z'").apply {
                    setLong(1, traceId)
                }.execute()
            }
        }
    }
}