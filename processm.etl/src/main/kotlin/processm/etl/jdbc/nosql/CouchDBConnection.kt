package processm.etl.jdbc.nosql

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.net.URL
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException

/**
 * A class pretending to be a JDBC DB connection for CouchDB. It is quite crude, most notably:
 * * it only supports prepared statements, and only related to data retrieval (CouchDB `_find` API available via executeQuery)
 *   and insert/update (via execute)
 * * it (possibly) makes a new connection for every REST API request instead of reusing one
 *
 * It also offers some convenience methods: [createDatabase] to create a new database, and [batchInsert] to insert
 * multiple documents in a single request
 *
 * Almost any method of the class will throw [SQLFeatureNotSupportedException]
 *
 * @param baseURL The base URL for the CouchDB instance, including credentials and the DB name. Must end with /.
 * @param client HttpClient to be used for making requests. The ownership of the client is transferred to the object.
 */
class CouchDBConnection internal constructor(
    private val baseURL: URL,
    username: String? = null,
    password: String? = null,
    private val client: HttpClient = HttpClient()
) :
    NotImplementedConnection() {

    /**
     * Auxiliary constructor ensuring URL ends with /
     */
    constructor(baseURL: String) : this(URL("$baseURL/"))

    constructor(server: String, port: Int, username: String?, password: String?, database: String, https: Boolean) :
            this(URL("${if (https) "https" else "http"}://$server:$port/$database/"), username, password)

    private val username: String?
    private val password: String?

    init {
        require(baseURL.path.endsWith("/"))
        with(baseURL.userInfo?.split(':', limit = 2)) {
            this@CouchDBConnection.username = username ?: this?.get(0)
            this@CouchDBConnection.password = password ?: this?.get(1)
        }
    }

    internal suspend fun post(url: String, block: HttpRequestBuilder.() -> Unit) =
        client.post(URL(baseURL, url)) {
            password?.let {
                basicAuth(username!!, password)
            }
            contentType(ContentType.Application.Json)
            block()
        }

    internal suspend fun put(url: String, block: HttpRequestBuilder.() -> Unit) =
        client.put(URL(baseURL, url)) {
            password?.let {
                basicAuth(username!!, password)
            }
            contentType(ContentType.Application.Json)
            block()
        }

    private fun check(value: Boolean) {
        if (!value) throw SQLException()
    }

    /**
     * Creates the database specified in the URL
     *
     * @throws SQLException if the operation failed
     */
    fun createDatabase() {
        runBlocking { check(put("") {}.status.isSuccess()) }
    }

    /**
     * Inserts all the documents specified by [docs] to the db
     *
     * @throws SQLException if the operation failed
     */
    fun batchInsert(docs: Iterator<JsonElement>) {
        runBlocking {
            check(post("_bulk_docs") {
                setBody(buildJsonObject { put("docs", buildJsonArray { docs.forEach(this::add) }) }.toString())
            }.status.isSuccess())
        }
    }

    /**
     * Closes the underlying HTTP client
     */
    override fun close() {
        client.close()
    }

    override fun prepareStatement(query: String): PreparedStatement = CouchDBPreparedStatement(this, query)
}