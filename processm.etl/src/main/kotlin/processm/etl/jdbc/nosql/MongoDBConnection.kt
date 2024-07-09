package processm.etl.jdbc.nosql

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import kotlinx.serialization.json.JsonElement
import org.bson.Document
import java.sql.PreparedStatement
import java.sql.SQLException

class MongoDBConnection(url: String, dbName: String, collectionName: String) : NotImplementedConnection() {

    companion object {
        /**
         * Expects the URL in the following format:
         * actual MongoDB URL '##' database name '##' collection name
         *
         * MongoDB has no standard way to pass database name and collection name as a part of an URL.
         * Moreover, # is a special character in URLs and I believe there cannot be two subsequent ## in an ordinary URL
         * Hence, this an inelegant, but otherwise viable solution to the problem.
         */
        fun fromProcessMUrl(url: String): MongoDBConnection {
            val parts = url.split("##", limit = 3)
            return MongoDBConnection(parts[0], parts[1], parts[2])
        }
    }

    internal val client: MongoClient
    internal val db: MongoDatabase
    internal val collection: MongoCollection<Document>

    init {
        client = MongoClients.create(url)
        db = client.getDatabase(dbName)
        collection = db.getCollection(collectionName)
    }

    /**
     * Inserts all the documents specified by [docs] to the db
     *
     * @throws SQLException if the operation failed
     */
    fun batchInsert(docs: List<JsonElement>) {
        collection.insertMany(docs.map { Document.parse(it.toString()) })
    }

    override fun close() {
        client.close()
    }

    override fun prepareStatement(query: String): PreparedStatement = MongoDBPreparedStatement(this, query)
}