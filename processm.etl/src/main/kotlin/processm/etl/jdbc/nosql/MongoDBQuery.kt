package processm.etl.jdbc.nosql

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.bson.Document

internal class MongoDBQuery(query: String) : JSONQuery() {
    override val parsed: QueryItem = substitute(Json.parseToJsonElement(query))

    fun bsonBind(values: List<JsonElement>) = Document.parse(bind(values))
}

