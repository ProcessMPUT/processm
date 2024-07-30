package processm.etl.jdbc.nosql

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.assertThrows
import java.sql.JDBCType
import java.sql.SQLException
import kotlin.test.*

class ResultSetFromJsonTest {

    @Test
    fun `detect double`() {
        val docs = listOf("""{"a": 1}""", """{"a": 1.5}""", """{"a": 2}""").map { Json.parseToJsonElement(it) }
        with(ResultSetFromJson(docs, columns = listOf(JsonPath("a")))) {
            assertEquals(JDBCType.DOUBLE.vendorTypeNumber, metaData.getColumnType(1))
        }
    }

    @Test
    fun `detect boolean`() {
        val docs = listOf("""{"a": 1}""", """{"a": 0}""", """{"a": "true"}""").map { Json.parseToJsonElement(it) }
        with(ResultSetFromJson(docs, columns = listOf(JsonPath("a")))) {
            assertEquals(JDBCType.BOOLEAN.vendorTypeNumber, metaData.getColumnType(1))
        }
    }

    @Test
    fun `detect string`() {
        val docs = listOf("""{"a": 1}""", """{"a": 0}""", """{"a": "lol"}""").map { Json.parseToJsonElement(it) }
        with(ResultSetFromJson(docs, columns = listOf(JsonPath("a")))) {
            assertEquals(JDBCType.VARCHAR.vendorTypeNumber, metaData.getColumnType(1))
        }
    }

    @Test
    fun `detect long`() {
        val docs = listOf("""{"a": 1}""", """{"a": 0}""", """{"a": 2}""").map { Json.parseToJsonElement(it) }
        with(ResultSetFromJson(docs, columns = listOf(JsonPath("a")))) {
            assertEquals(JDBCType.BIGINT.vendorTypeNumber, metaData.getColumnType(1))
        }
    }

    @Test
    fun `detect columns`() {
        val docs = listOf(
            """{"a": {"b": 1}}""",
            """{"a": {"c": 1}}""",
            """{"b": [1,2,3], "d": null}"""
        ).map { Json.parseToJsonElement(it) }
        with(ResultSetFromJson(docs).metaData) {
            assertEquals(6, columnCount)
            assertEquals(
                listOf("a.b", "a.c", "b.0", "b.1", "b.2", "d"),
                (1..columnCount).map { getColumnName(it) }.sorted()
            )
        }
    }

    @Test
    fun `sequential read`() {
        val docs = listOf(
            """{"a": {"b": 1}}""",
            """{"a": {"c": "true"}}""",
            """{"b": [1,2,3], "d": null}"""
        ).map { Json.parseToJsonElement(it) }
        with(
            ResultSetFromJson(
                docs,
                columns = listOf("a.b", "a.c", "b", "d").map { JsonPath(it) },
                types = listOf(JDBCType.BIGINT, JDBCType.BOOLEAN, JDBCType.ARRAY, JDBCType.NULL)
            )
        ) {
            assertTrue { isBeforeFirst && !isFirst && !isLast && !isAfterLast }
            next()
            assertTrue { !isBeforeFirst && isFirst && !isLast && !isAfterLast }
            assertEquals(1, getLong(1))
            assertEquals(false, getBoolean(2))
            assertNull(getObject(3))
            assertNull(getObject(4))
            next()
            assertTrue { !isBeforeFirst && !isFirst && !isLast && !isAfterLast }
            assertEquals(0, getLong(1))
            assertEquals(true, getBoolean(2))
            assertNull(getObject(3))
            assertNull(getObject(4))
            next()
            assertTrue { !isBeforeFirst && !isFirst && isLast && !isAfterLast }
            assertEquals(0, getLong(1))
            assertEquals(false, getBoolean(2))
            assertIs<JsonArray>(getObject(3))
            assertNull(getObject(4))
            next()
            assertTrue { !isBeforeFirst && !isFirst && !isLast && isAfterLast }
        }
    }

    @Test
    fun `absolute read`() {
        val docs = listOf(
            """{"a": 1}""",
            """{"a": 2}""",
            """{"a": 3}"""
        ).map { Json.parseToJsonElement(it) }
        with(
            ResultSetFromJson(docs, columns = listOf("a").map { JsonPath(it) }, types = listOf(JDBCType.BIGINT))
        ) {
            absolute(0)
            assertTrue { isBeforeFirst }
            absolute(3)
            assertTrue { isLast }
            assertEquals(3, getInt(1))
            absolute(2)
            assertEquals(2, getInt(1))
            absolute(17)
            assertTrue { isAfterLast }
        }
    }


    @Test
    fun `relative read`() {
        val docs = listOf(
            """{"a": 1}""",
            """{"a": 2}""",
            """{"a": 3}"""
        ).map { Json.parseToJsonElement(it) }
        with(
            ResultSetFromJson(docs, columns = listOf("a").map { JsonPath(it) }, types = listOf(JDBCType.BIGINT))
        ) {
            assertTrue { isBeforeFirst }
            relative(0)
            assertTrue { isBeforeFirst }
            relative(3)
            assertTrue { isLast }
            assertEquals(3, getInt(1))
            relative(-1)
            assertEquals(2, getInt(1))
            relative(999)
            assertTrue { isAfterLast }
            relative(-1)
            assertTrue { isLast }
            assertEquals(3, getInt(1))
        }
    }

    @Test
    fun `findColumn can handle various surface forms`() {
        with(
            ResultSetFromJson(emptyList(), columns = listOf(JsonPath(listOf("a.b"))), types = listOf(JDBCType.BIGINT))
        ) {
            assertThrows<SQLException> { findColumn("a.b") }
            assertEquals(1, findColumn("a\\.b"))
            assertEquals(1, findColumn("\"a.b\""))
            assertEquals(1, findColumn("\"\"\"a.b\""))
        }
    }
}