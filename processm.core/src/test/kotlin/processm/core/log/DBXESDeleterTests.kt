package processm.core.log

import org.junit.jupiter.api.Tag
import processm.core.DBTestHelper
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import java.sql.Statement
import java.util.*
import kotlin.test.*

@Tag("PQL")
class DBXESDeleterTests {
    companion object {
        // These are statically loaded logs that are immutable and must remain in the database after tests. The logs
        // for removal are inserted by individual tests
        private val journal: UUID = DBTestHelper.JournalReviewExtra
        private val bpi: UUID = DBTestHelper.BPIChallenge2013OpenProblems
        private val hospital: UUID = DBTestHelper.HospitalLog
        private val baseTotals = Totals.get()
    }

    @Test
    fun `delete a copy of JournalReview_extra`() {
        val toRemove = insert()

        DBXESDeleter(DBTestHelper.dbName, Query("delete log where l:id=$toRemove")).invoke()

        // verify whether a single copy of JournalReview is stored in the database
        val after = q("select count(l:name) where l:name='JournalReview'").first()
        assertEquals(1, after.count)
        assertEquals(baseTotals, Totals.get()) // make sure that all rows were removed
    }

    @Test
    fun `delete all traces from JournalReview_extra but not a log itself`() {
        val toRemove = insert()

        try {
            DBXESDeleter(DBTestHelper.dbName, Query("delete trace where l:id=$toRemove")).invoke()

            // verify whether two copies of JournalReview are still stored in the database but the second copy is empty
            val after = q("select count(l:name), count(^t:name) where l:name='JournalReview'").first()
            assertEquals(2, after.count)
            assertEquals(101L, after.attributes["count(^trace:concept:name)"])
            assertEquals(0, q("where l:id=$toRemove").first().traces.count())
            val currentTotals = Totals.get()

            assertNotEquals(baseTotals.logs, currentTotals.logs)
            assertNotEquals(baseTotals.logAttributes, currentTotals.logAttributes)
            assertEquals(baseTotals.traces, currentTotals.traces)
            assertEquals(baseTotals.traceAttributes, currentTotals.traceAttributes)
            assertEquals(baseTotals.events, currentTotals.events)
            assertEquals(baseTotals.eventAttributes, currentTotals.eventAttributes)
        } finally {
            DBCache.get(DBTestHelper.dbName).getConnection().use { conn ->
                DBLogCleaner.removeLog(conn, toRemove)
            }
        }
    }

    @Test
    fun `delete from JournalReview_extra the traces with cost_total unset`() {
        val toRemove = insert()

        try {
            DBXESDeleter(DBTestHelper.dbName, Query("delete trace where l:id=$toRemove and t:total is null")).invoke()

            // verify whether two copies of JournalReview are still stored in the database
            val after = q("select count(l:name), count(^t:name) where l:name='JournalReview'").first()
            assertEquals(2, after.count)
            assertEquals(151L, after.attributes["count(^trace:concept:name)"])

            // verify whether all remaining traces have the cost:total set
            val remaining = q("where l:id=$toRemove")
            assertEquals(50, remaining.first().traces.count())
            for (trace in remaining.first().traces)
                assertNotNull(trace.costTotal)

            // verify totals
            val currentTotals = Totals.get()
            assertNotEquals(baseTotals.logs, currentTotals.logs)
            assertNotEquals(baseTotals.logAttributes, currentTotals.logAttributes)
            assertNotEquals(baseTotals.traces, currentTotals.traces)
            assertNotEquals(baseTotals.traceAttributes, currentTotals.traceAttributes)
            assertNotEquals(baseTotals.events, currentTotals.events)
            assertNotEquals(baseTotals.eventAttributes, currentTotals.eventAttributes)
        } finally {
            DBCache.get(DBTestHelper.dbName).getConnection().use { conn ->
                DBLogCleaner.removeLog(conn, toRemove)
            }
        }
    }

    @Test
    fun `delete all events from JournalReview_extra but not the traces and the log themselves`() {
        val toRemove = insert()
        try {
            DBXESDeleter(DBTestHelper.dbName, Query("delete event where l:id=$toRemove")).invoke()

            // verify whether two copies of JournalReview are still stored in the database but the traces are empty
            val after = q("select count(l:name), count(^t:name), count(^^e:name) where l:name='JournalReview'").first()
            assertEquals(2L, after.attributes["count(log:concept:name)"])
            assertEquals(202L, after.attributes["count(^trace:concept:name)"])
            assertEquals(2298L, after.attributes["count(^^event:concept:name)"])

            val removed = q("select count(l:name), count(^t:name), count(^^e:name) where l:id=$toRemove").first()
            assertEquals(1L, removed.attributes["count(log:concept:name)"])
            assertEquals(101L, removed.attributes["count(^trace:concept:name)"])
            assertEquals(0L, removed.attributes["count(^^event:concept:name)"])

            // verify totals
            val currentTotals = Totals.get()
            assertNotEquals(baseTotals.logs, currentTotals.logs)
            assertNotEquals(baseTotals.logAttributes, currentTotals.logAttributes)
            assertNotEquals(baseTotals.traces, currentTotals.traces)
            assertNotEquals(baseTotals.traceAttributes, currentTotals.traceAttributes)
            assertEquals(baseTotals.events, currentTotals.events)
            assertEquals(baseTotals.eventAttributes, currentTotals.eventAttributes)
        } finally {
            DBCache.get(DBTestHelper.dbName).getConnection().use { conn ->
                DBLogCleaner.removeLog(conn, toRemove)
            }
        }
    }

    @Test
    fun `delete from JournalReview_extra the additional review events`() {
        val toRemove = insert()

        try {
            DBXESDeleter(
                DBTestHelper.dbName,
                Query("delete event where l:id=$toRemove and e:name in ('invite additional reviewer', 'get review X', 'time-out X')")
            ).invoke()

            // verify whether two copies of JournalReview are still stored in the database
            val after = q("select count(l:name), count(^t:name) where l:name='JournalReview'").first()
            assertEquals(2, after.count)
            assertEquals(202L, after.attributes["count(^trace:concept:name)"])

            // verify whether all traces remain and have all but additional review events
            val remaining = q("where l:id=$toRemove")
            assertEquals(101, remaining.first().traces.count())
            for (trace in remaining.first().traces) {
                assertEquals("invite reviewers", trace.events.first().conceptName)
                if (trace.conceptName != "-1")
                    assertTrue(trace.events.last().conceptName.let { it == "accept" || it == "reject" })
                assertTrue("invite additional reviewer" !in trace.events.map { it.conceptName })
                assertTrue("get review X" !in trace.events.map { it.conceptName })
                assertTrue("time-out X" !in trace.events.map { it.conceptName })
            }

            // verify totals
            val currentTotals = Totals.get()
            assertNotEquals(baseTotals.logs, currentTotals.logs)
            assertNotEquals(baseTotals.logAttributes, currentTotals.logAttributes)
            assertNotEquals(baseTotals.traces, currentTotals.traces)
            assertNotEquals(baseTotals.traceAttributes, currentTotals.traceAttributes)
            assertNotEquals(baseTotals.events, currentTotals.events)
            assertNotEquals(baseTotals.eventAttributes, currentTotals.eventAttributes)
        } finally {
            DBCache.get(DBTestHelper.dbName).getConnection().use { conn ->
                DBLogCleaner.removeLog(conn, toRemove)
            }
        }
    }

    private fun q(pql: String): DBHierarchicalXESInputStream =
        DBHierarchicalXESInputStream(DBTestHelper.dbName, Query(pql))

    private fun insert(): UUID =
        this::class.java.getResourceAsStream("/xes-logs/JournalReview-extra.xes").use { DBTestHelper.loadLog(it) }
            .also {
                // verify whether two copies of JournalReview are stored in the database
                val before = q("select count(l:name), count(^t:name) where l:name='JournalReview'").first()
                assertEquals(2, before.count)
                assertEquals(202L, before.attributes["count(^trace:concept:name)"])
                assertNotEquals(baseTotals, Totals.get())
            }

    data class Totals(
        val logs: Int,
        val logAttributes: Int,
        val traces: Int,
        val traceAttributes: Int,
        val events: Int,
        val eventAttributes: Int
    ) {
        companion object {
            fun get(): Totals =
                DBCache.get(DBTestHelper.dbName).getConnection().use { conn ->
                    conn.prepareStatement(
                        "SELECT count(*) FROM logs; SELECT count(*) FROM logs_attributes; SELECT count(*) FROM traces; SELECT count(*) FROM traces_attributes; SELECT count(*) FROM events; SELECT count(*) FROM events_attributes"
                    ).use { stmt ->
                        stmt.execute()
                        with(stmt) {
                            Totals(next(), next(), next(), next(), next(), next())
                        }
                    }
                }

            private fun Statement.next(): Int {
                resultSet.next()
                val res = resultSet.getInt(1)
                getMoreResults(Statement.CLOSE_CURRENT_RESULT)
                return res
            }
        }
    }
}
