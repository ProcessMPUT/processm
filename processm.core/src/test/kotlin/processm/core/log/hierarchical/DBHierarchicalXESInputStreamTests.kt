package processm.core.log.hierarchical

import org.junit.jupiter.api.BeforeAll
import processm.core.DBTestHelper.dbName
import processm.core.helpers.hierarchicalCompare
import processm.core.log.*
import processm.core.log.attribute.Attribute.Companion.CONCEPT_NAME
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DBHierarchicalXESInputStreamTests {
    companion object {
        val log: Sequence<Log> =
            sequenceOf(Log(sequenceOf(
                Trace(sequenceOf(
                    Event().apply { conceptName = "A"; setCustomAttributes(emptyMap()) },
                    Event().apply { conceptName = "B"; setCustomAttributes(emptyMap()) },
                    Event().apply { conceptName = "C"; setCustomAttributes(emptyMap()) }
                )).apply {
                    conceptName = "T"
                    identityId = UUID.randomUUID()
                    setCustomAttributes(emptyMap())
                },
                Trace(
                    sequenceOf(
                        Event().apply { conceptName = "D"; setCustomAttributes(emptyMap()) },
                        Event().apply { conceptName = "E"; setCustomAttributes(emptyMap()) },
                        Event().apply { conceptName = "F"; setCustomAttributes(emptyMap()) }
                    )).apply {
                    conceptName = "U"
                    identityId = UUID.randomUUID()
                    setCustomAttributes(emptyMap())
                }
            )).apply {
                conceptName = "L"
                identityId = UUID.randomUUID()
                setCustomAttributes(emptyMap())
            })

        @BeforeAll
        @JvmStatic
        fun setUp() {
            DBXESOutputStream(DBCache.get(dbName).getConnection()).use {
                it.write(log.toFlatSequence())
            }
        }
    }

    @Test
    fun castTest() {
        val fromDB = DBHierarchicalXESInputStream(dbName, Query("where l:id=${log.first().identityId}"))
        var implicitCast: XESInputStream = fromDB
        assertNotNull(implicitCast)

        implicitCast = fromDB.toFlatSequence()
        assertNotNull(implicitCast)
    }

    @Test
    fun repeatableReadTest() {
        val fromDB = DBHierarchicalXESInputStream(dbName, Query("where l:id=${log.first().identityId}"))
        assertTrue(hierarchicalCompare(log, fromDB))
        assertTrue(hierarchicalCompare(log, fromDB))
    }

    @Test
    fun repeatableInterleavedReadTest() {
        val fromDB = DBHierarchicalXESInputStream(dbName, Query("where l:id=${log.first().identityId}"))
        assertTrue(hierarchicalCompare(fromDB, fromDB))
        assertTrue(hierarchicalCompare(fromDB, fromDB))
    }

    @Test
    fun phantomReadTest() {
        try {
            val fromDB = DBHierarchicalXESInputStream(dbName, Query("where l:id=${log.first().identityId}"))
            assertTrue(hierarchicalCompare(log, fromDB))

            // insert phantom event Z into trace T
            AppendingDBXESOutputStream(DBCache.get(dbName).getConnection()).use { out ->
                out.write(log.first())
                out.write(log.first().traces.first())
                out.write(Event(MutableAttributeMap().also {
                    it[CONCEPT_NAME] = "Z"
                }))
            }

            // implementation should ensure that phantom reads do not occur
            assertTrue(hierarchicalCompare(log, fromDB))
            // but a new sequence should reflect changes
            val fromDB2 = DBHierarchicalXESInputStream(dbName, Query("where l:id=${log.first().identityId}"))
            assertFalse(hierarchicalCompare(log, fromDB2))
        } finally {
            // Delete the phantom event
            // TODO("Replace with API call when issue #79 is complete")
            DBCache.get(dbName).getConnection().use { conn ->
                conn.prepareStatement("DELETE FROM events WHERE trace_id=(SELECT id FROM traces WHERE \"identity:id\"=?::uuid) AND \"concept:name\"='Z'")
                    .apply {
                        setObject(1, log.first().traces.first().identityId)
                    }.execute()
            }
        }
    }
}
