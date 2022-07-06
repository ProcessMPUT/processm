package processm.core.log

import processm.core.DBTestHelper
import processm.core.log.attribute.Attribute.Companion.CONCEPT_NAME
import processm.core.log.attribute.Attribute.Companion.IDENTITY_ID
import processm.core.log.attribute.IDAttr
import processm.core.log.attribute.StringAttr
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

        val log = Log(mutableMapOf(IDENTITY_ID to IDAttr(IDENTITY_ID, logUUID)))
        val trace1 = Trace(mutableMapOf(IDENTITY_ID to IDAttr(IDENTITY_ID, trace1UUID)))
        val trace2 = Trace(mutableMapOf(IDENTITY_ID to IDAttr(IDENTITY_ID, trace2UUID)))
        val trace3 = Trace(mutableMapOf(IDENTITY_ID to IDAttr(IDENTITY_ID, trace3UUID)))

        val events1 = sequenceOf(
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "create order"))),
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "issue invoice"))),
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "pay"))),
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "deliver"))),
        )

        val events2 = sequenceOf(
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "create order"))),
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "backorder"))),
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "issue invoice"))),
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "pay"))),
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "deliver"))),
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "complaint"))),
        )

        val events3 = sequenceOf(
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "create order"))),
            Event(mutableMapOf(CONCEPT_NAME to StringAttr(CONCEPT_NAME, "change quantity"))),
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
