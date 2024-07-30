package processm.etl.jdbc.nosql

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import java.sql.ResultSet


internal class MongoDBPreparedStatement(val connection: MongoDBConnection, query: String) : NoSQLPreparedStatement() {

    private val query = MongoDBQuery(query)
    override val params = MutableList<JsonElement>(this.query.parameterCount) { JsonNull }

    override fun executeQuery(): ResultSet {
        // FIXME that is abhorrent, a compatibility layer should be introduced instead
        return ResultSetFromJson(
            connection.collection.find(query.bsonBind(params))
                .mapTo(ArrayList()) { Json.parseToJsonElement(it.toJson()) })
    }
}