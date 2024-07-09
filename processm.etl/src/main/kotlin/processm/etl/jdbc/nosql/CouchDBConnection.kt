package processm.etl.jdbc.nosql

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.net.URL
import java.sql.*
import java.util.*
import java.util.concurrent.Executor

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
class CouchDBConnection internal constructor(private val baseURL: URL, private val client: HttpClient = HttpClient()) :
    Connection {

    /**
     * Auxiliary constructor ensuring URL ends with /
     */
    constructor(baseURL: String) : this(URL("$baseURL/"))

    private val username: String?
    private val password: String?

    init {
        require(baseURL.path.endsWith("/"))
        with(baseURL.userInfo?.split(':', limit = 2)) {
            username = this?.get(0)
            password = this?.get(1)
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

    override fun <T : Any?> unwrap(p0: Class<T>?): T {
        intentionallyNotImplemented()
    }

    override fun isWrapperFor(p0: Class<*>?): Boolean {
        intentionallyNotImplemented()
    }

    /**
     * Closes the underlying HTTP client
     */
    override fun close() {
        client.close()
    }

    override fun createStatement(): Statement {
        intentionallyNotImplemented()
    }

    override fun createStatement(p0: Int, p1: Int): Statement {
        intentionallyNotImplemented()
    }

    override fun createStatement(p0: Int, p1: Int, p2: Int): Statement {
        intentionallyNotImplemented()
    }

    override fun prepareStatement(query: String): PreparedStatement = CouchDBPreparedStatement(this, query)

    override fun prepareStatement(p0: String?, p1: Int, p2: Int): PreparedStatement {
        intentionallyNotImplemented()
    }

    override fun prepareStatement(p0: String?, p1: Int, p2: Int, p3: Int): PreparedStatement {
        intentionallyNotImplemented()
    }

    override fun prepareStatement(p0: String?, p1: Int): PreparedStatement {
        intentionallyNotImplemented()
    }

    override fun prepareStatement(p0: String?, p1: IntArray?): PreparedStatement {
        intentionallyNotImplemented()
    }

    override fun prepareStatement(p0: String?, p1: Array<out String>?): PreparedStatement {
        intentionallyNotImplemented()
    }

    override fun prepareCall(p0: String?): CallableStatement {
        intentionallyNotImplemented()
    }

    override fun prepareCall(p0: String?, p1: Int, p2: Int): CallableStatement {
        intentionallyNotImplemented()
    }

    override fun prepareCall(p0: String?, p1: Int, p2: Int, p3: Int): CallableStatement {
        intentionallyNotImplemented()
    }

    override fun nativeSQL(p0: String?): String {
        intentionallyNotImplemented()
    }

    override fun setAutoCommit(p0: Boolean) {
        intentionallyNotImplemented()
    }

    override fun getAutoCommit(): Boolean {
        intentionallyNotImplemented()
    }

    override fun commit() {
        intentionallyNotImplemented()
    }

    override fun rollback() {
        intentionallyNotImplemented()
    }

    override fun rollback(p0: Savepoint?) {
        intentionallyNotImplemented()
    }

    override fun isClosed(): Boolean {
        intentionallyNotImplemented()
    }

    override fun getMetaData(): DatabaseMetaData {
        intentionallyNotImplemented()
    }

    override fun setReadOnly(p0: Boolean) {
        intentionallyNotImplemented()
    }

    override fun isReadOnly(): Boolean {
        intentionallyNotImplemented()
    }

    override fun setCatalog(p0: String?) {
        intentionallyNotImplemented()
    }

    override fun getCatalog(): String {
        intentionallyNotImplemented()
    }

    override fun setTransactionIsolation(p0: Int) {
        intentionallyNotImplemented()
    }

    override fun getTransactionIsolation(): Int {
        intentionallyNotImplemented()
    }

    override fun getWarnings(): SQLWarning {
        intentionallyNotImplemented()
    }

    override fun clearWarnings() {
        intentionallyNotImplemented()
    }

    override fun getTypeMap(): MutableMap<String, Class<*>> {
        intentionallyNotImplemented()
    }

    override fun setTypeMap(p0: MutableMap<String, Class<*>>?) {
        intentionallyNotImplemented()
    }

    override fun setHoldability(p0: Int) {
        intentionallyNotImplemented()
    }

    override fun getHoldability(): Int {
        intentionallyNotImplemented()
    }

    override fun setSavepoint(): Savepoint {
        intentionallyNotImplemented()
    }

    override fun setSavepoint(p0: String?): Savepoint {
        intentionallyNotImplemented()
    }

    override fun releaseSavepoint(p0: Savepoint?) {
        intentionallyNotImplemented()
    }

    override fun createClob(): Clob {
        intentionallyNotImplemented()
    }

    override fun createBlob(): Blob {
        intentionallyNotImplemented()
    }

    override fun createNClob(): NClob {
        intentionallyNotImplemented()
    }

    override fun createSQLXML(): SQLXML {
        intentionallyNotImplemented()
    }

    override fun isValid(p0: Int): Boolean {
        intentionallyNotImplemented()
    }

    override fun setClientInfo(p0: String?, p1: String?) {
        intentionallyNotImplemented()
    }

    override fun setClientInfo(p0: Properties?) {
        intentionallyNotImplemented()
    }

    override fun getClientInfo(p0: String?): String {
        intentionallyNotImplemented()
    }

    override fun getClientInfo(): Properties {
        intentionallyNotImplemented()
    }

    override fun createArrayOf(p0: String?, p1: Array<out Any>?): java.sql.Array {
        intentionallyNotImplemented()
    }

    override fun createStruct(p0: String?, p1: Array<out Any>?): Struct {
        intentionallyNotImplemented()
    }

    override fun setSchema(p0: String?) {
        intentionallyNotImplemented()
    }

    override fun getSchema(): String {
        intentionallyNotImplemented()
    }

    override fun abort(p0: Executor?) {
        intentionallyNotImplemented()
    }

    override fun setNetworkTimeout(p0: Executor?, p1: Int) = intentionallyNotImplemented()

    override fun getNetworkTimeout(): Int = intentionallyNotImplemented()

    private fun intentionallyNotImplemented(): Nothing =
        throw SQLFeatureNotSupportedException("Intentionally not implemented")
}