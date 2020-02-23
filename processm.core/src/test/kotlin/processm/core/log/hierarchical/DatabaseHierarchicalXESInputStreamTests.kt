package processm.core.log.hierarchical

import processm.core.log.DatabaseXESOutputStream
import processm.core.log.Event
import processm.core.log.XESInputStream
import processm.core.persistence.DBConnectionPool
import kotlin.test.*

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

    fun phantomReadTest() {
        TODO()
    }

    private fun hierarchicalCompare(seq1: Sequence<Log>, seq2: Sequence<Log>): Boolean =
        seq1.zip(seq2).all { (l1, l2) ->
            l1.conceptName == l2.conceptName && l1.traces.zip(l2.traces).all { (t1, t2) ->
                t1.conceptName == t2.conceptName && t1.events.zip(t2.events).all { (e1, e2) ->
                    e1.conceptName == e2.conceptName
                }
            }
        }
}