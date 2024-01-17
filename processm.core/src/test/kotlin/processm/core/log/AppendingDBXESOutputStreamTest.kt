package processm.core.log

import processm.core.DBTestHelper
import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.Attribute.IDENTITY_ID
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.toFlatSequence
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AppendingDBXESOutputStreamTest {
    companion object {
        val dbName = DBTestHelper.dbName
        val logUUID = UUID.randomUUID()
        val trace1UUID = UUID.randomUUID()
        val trace2UUID = UUID.randomUUID()
        val trace3UUID = UUID.randomUUID()

        val log = Log(mutableAttributeMapOf(IDENTITY_ID to logUUID))
        val trace1 = Trace(mutableAttributeMapOf(IDENTITY_ID to trace1UUID))
        val trace2 = Trace(mutableAttributeMapOf(IDENTITY_ID to trace2UUID))
        val trace3 = Trace(mutableAttributeMapOf(IDENTITY_ID to trace3UUID))

        val events1 = sequenceOf(
            Event(mutableAttributeMapOf(CONCEPT_NAME to "create order")),
            Event(mutableAttributeMapOf(CONCEPT_NAME to "issue invoice")),
            Event(mutableAttributeMapOf(CONCEPT_NAME to "pay")),
            Event(mutableAttributeMapOf(CONCEPT_NAME to "deliver")),
        )

        val events2 = sequenceOf(
            Event(mutableAttributeMapOf(CONCEPT_NAME to "create order")),
            Event(mutableAttributeMapOf(CONCEPT_NAME to "backorder")),
            Event(mutableAttributeMapOf(CONCEPT_NAME to "issue invoice")),
            Event(mutableAttributeMapOf(CONCEPT_NAME to "pay").apply { children("other name")["floatKey"] = 1.23 }),
            Event(mutableAttributeMapOf(CONCEPT_NAME to "deliver").apply {
                children("some name")["booleanKey"] = true
            }),
            Event(mutableAttributeMapOf(CONCEPT_NAME to "complaint")),
        )

        val events3 = sequenceOf(
            Event(mutableAttributeMapOf(CONCEPT_NAME to "create order")),
            Event(mutableAttributeMapOf(CONCEPT_NAME to "change quantity")),
        )

        val part1 = sequenceOf(log, trace1) + events1.take(2) + sequenceOf(trace2) + events2.take(2)

        val part2 = sequenceOf(log, trace2) + events2.drop(2).take(2) + sequenceOf(trace1) + events1.drop(2).take(2)

        val part3 = sequenceOf(log, trace2) + events2.drop(4) + sequenceOf(trace3) + events3.take(1)

        val part4 = sequenceOf(log, trace1 /* anomaly: empty trace */, trace3) + events3.drop(1)

        val duplicates = sequenceOf(log, trace1) + events1.take(1) + sequenceOf(trace2) + events2.take(1) +
                sequenceOf(trace1) + events1.drop(1).take(1) + sequenceOf(trace2) + events2.drop(1).take(1) +
                sequenceOf(trace1) + events1.drop(2).take(1) + sequenceOf(trace2) + events2.drop(2).take(1) +
                sequenceOf(trace1) + events1.drop(3).take(1) + sequenceOf(trace2) + events2.drop(3).take(1)
    }

    @AfterTest
    fun cleanUp() {
        DBCache.get(dbName).getConnection().use { conn ->
            conn.autoCommit = false
            conn.prepareStatement("""SELECT id FROM logs WHERE "identity:id"='$logUUID'::uuid""").executeQuery().use {
                while (it.next())
                    DBLogCleaner.removeLog(conn, it.getInt(1))
            }
            conn.commit()
        }
    }

    @Test
    fun `Create a partial log`() {
        save(part1)
        expect(part1)
    }

    @Test
    fun `Create a partial log and append one part`() {
        save(part1)
        save(part2)
        expect(sequenceOf(log, trace1) + events1.take(4) + sequenceOf(trace2) + events2.take(4))
    }

    @Test
    fun `Create a partial log and append two parts`() {
        save(part1)
        save(part2)
        save(part3)
        expect(sequenceOf(log, trace1) + events1 + sequenceOf(trace2) + events2 + sequenceOf(trace3) + events3.take(1))
    }

    @Test
    fun `Create a partial log and append parts with anomaly`() {
        save(part1)
        save(part2)
        save(part3)
        save(part4)
        expect(sequenceOf(log, trace1) + events1 + sequenceOf(trace2) + events2 + sequenceOf(trace3) + events3)
    }

    @Test
    fun `Create a partial log from stream with duplicate traces`() {
        save(duplicates)
        expect(sequenceOf(log, trace1) + events1 + sequenceOf(trace2) + events2.take(4))
    }

    @Test
    fun `save everything at once`() {
        save(part1 + part2 + part3 + part4)
        expect(sequenceOf(log, trace1) + events1 + sequenceOf(trace2) + events2 + sequenceOf(trace3) + events3)
    }

    @Test
    fun `prepend every event with log and trace`() {
        save(events1.flatMap { sequenceOf(log, trace1, it) }
                + events2.flatMap { sequenceOf(log, trace2, it) }
                + events3.flatMap { sequenceOf(log, trace3, it) }
        )
        expect(sequenceOf(log, trace1) + events1 + sequenceOf(trace2) + events2 + sequenceOf(trace3) + events3)
    }

    private fun save(part: Sequence<XESComponent>) {
        AppendingDBXESOutputStream(DBCache.get(dbName).getConnection()).use { stream ->
            stream.write(part)
        }
    }

    private fun expect(expected: Sequence<XESComponent>) {
        val stream = DBHierarchicalXESInputStream(dbName, Query("where l:id=$logUUID"))
        assertEquals(1, stream.count())
        assertContentEquals(expected, stream.first().toFlatSequence())
    }
}
