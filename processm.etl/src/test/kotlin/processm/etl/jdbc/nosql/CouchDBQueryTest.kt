package processm.etl.jdbc.nosql

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class CouchDBQueryTest {

    @Test
    fun `one param`() {
        val text = """{
    "selector": {
        "year": {"${'$'}gt": "?"}
    },
    "fields": ["_id", "_rev", "year", "title"],
    "sort": [{"year": "asc"}],
    "limit": 2,
    "skip": 0,
    "execution_stats": true
}"""
        val query = CouchDBQuery(text)
        val actual = Json.parseToJsonElement(query.bind(listOf(JsonPrimitive(123))))
        val expected = Json.parseToJsonElement(
            """{
    "selector": {
        "year": {"${'$'}gt": 123}
    },
    "fields": ["_id", "_rev", "year", "title"],
    "sort": [{"year": "asc"}],
    "limit": 2,
    "skip": 0,
    "execution_stats": true,
    "bookmark": null
}"""
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `two params and bookmark`() {
        val text = """{
    "selector": {
        "year": {"${'$'}gt": "?"}
    },
    "fields": ["_id", "_rev", "year", "title"],
    "sort": [{"year": "asc"}],
    "limit": "?",
    "skip": 0,
    "execution_stats": true
}"""
        val query = CouchDBQuery(text)
        val actual =
            Json.parseToJsonElement(query.bind(listOf(JsonPrimitive(123), JsonPrimitive(2)), "somebookmark"))
        val expected = Json.parseToJsonElement(
            """{
    "selector": {
        "year": {"${'$'}gt": 123}
    },
    "fields": ["_id", "_rev", "year", "title"],
    "sort": [{"year": "asc"}],
    "limit": 2,
    "skip": 0,
    "execution_stats": true,
    "bookmark": "somebookmark"
}"""
        )
        assertEquals(expected, actual)
    }


    @Test
    fun escaping() {
        val text = """{
    "1": "??",
    "2": "???",
    "3": "?!?"
}"""
        val query = CouchDBQuery(text)
        val actual = Json.parseToJsonElement(query.bind(emptyList()))
        val expected = Json.parseToJsonElement(
            """{
    "1": "?",
    "2": "??",
    "3": "?!?",
    "bookmark": null
}"""
        )
        assertEquals(expected, actual)
    }
}