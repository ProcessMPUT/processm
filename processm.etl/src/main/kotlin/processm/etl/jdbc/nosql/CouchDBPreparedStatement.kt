package processm.etl.jdbc.nosql

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.sql.ResultSet


internal class CouchDBPreparedStatement(val connection: CouchDBConnection, query: String) : NoSQLPreparedStatement() {

    private val query = CouchDBQuery(query)
    override val params = MutableList<JsonElement>(this.query.parameterCount) { JsonNull }

    override fun executeQuery(): ResultSet {
        return runBlocking {
            //FIXME It seems quite wasteful to eagerly retrieve all the data, but I don't know how to infer the column names and types otherwise
            var bookmark: String? = null
            val docs = ArrayList<JsonElement>()
            while (true) {
                val httpResponse = connection.post("_find") {
                    setBody(query.bind(params, bookmark = bookmark))
                }
                check(httpResponse.status.isSuccess())
                val chunk = Json.parseToJsonElement(httpResponse.body())
                val chunkDocs = runCatching { chunk.jsonObject["docs"]?.jsonArray }.getOrNull() ?: break
                if (chunkDocs.isNotEmpty()) {
                    docs.addAll(chunkDocs)
                } else
                    break
                bookmark = chunk.jsonObject["bookmark"]?.jsonPrimitive?.content ?: break
            }
            ResultSetFromJson(docs)
        }
    }

    /**
     * Inserts data
     */
    override fun execute(): Boolean {
        runBlocking {
            val httpResponse = connection.post("") {
                setBody(query.bind(params))
            }
            check(httpResponse.status.isSuccess())
        }
        return false
    }
}