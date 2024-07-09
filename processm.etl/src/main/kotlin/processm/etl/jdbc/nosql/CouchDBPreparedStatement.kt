package processm.etl.jdbc.nosql

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.net.URL
import java.sql.*
import java.sql.Array
import java.sql.Date
import java.util.*


internal class CouchDBPreparedStatement(val connection: CouchDBConnection, query: String) : CouchDBStatement(),
    PreparedStatement {

    private val query = CouchDBQuery(query)
    private val params = MutableList<JsonElement>(this.query.parameterCount) { JsonNull }

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

    private fun intentionallyNotImplemented(): Nothing =
        throw SQLFeatureNotSupportedException("Intentionally not implemented")

    override fun executeUpdate(): Int {
        intentionallyNotImplemented()
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

    override fun addBatch() {
        intentionallyNotImplemented()
    }

    override fun setNull(p0: Int, p1: Int) {
        intentionallyNotImplemented()
    }

    override fun setNull(p0: Int, p1: Int, p2: String?) {
        intentionallyNotImplemented()
    }

    override fun setBoolean(p0: Int, p1: Boolean) {
        intentionallyNotImplemented()
    }

    override fun setByte(p0: Int, p1: Byte) {
        intentionallyNotImplemented()
    }

    override fun setShort(p0: Int, p1: Short) {
        intentionallyNotImplemented()
    }

    override fun setInt(parameterIndex: Int, x: Int) = setLong(parameterIndex, x.toLong())

    override fun setLong(parameterIndex: Int, x: Long) {
        params[parameterIndex - 1] = Json.encodeToJsonElement(x)
    }

    override fun setFloat(p0: Int, p1: Float) {
        intentionallyNotImplemented()
    }

    override fun setDouble(p0: Int, p1: Double) {
        intentionallyNotImplemented()
    }

    override fun setBigDecimal(p0: Int, p1: BigDecimal?) {
        intentionallyNotImplemented()
    }

    override fun setString(parameterIndex: Int, x: String) {
        params[parameterIndex - 1] = Json.encodeToJsonElement(x)
    }

    override fun setBytes(p0: Int, p1: ByteArray?) {
        intentionallyNotImplemented()
    }

    override fun setDate(p0: Int, p1: Date?) {
        intentionallyNotImplemented()
    }

    override fun setDate(p0: Int, p1: Date?, p2: Calendar?) {
        intentionallyNotImplemented()
    }

    override fun setTime(p0: Int, p1: Time?) {
        intentionallyNotImplemented()
    }

    override fun setTime(p0: Int, p1: Time?, p2: Calendar?) {
        intentionallyNotImplemented()
    }

    override fun setTimestamp(p0: Int, p1: Timestamp?) {
        intentionallyNotImplemented()
    }

    override fun setTimestamp(p0: Int, p1: Timestamp?, p2: Calendar?) {
        intentionallyNotImplemented()
    }

    override fun setAsciiStream(p0: Int, p1: InputStream?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun setAsciiStream(p0: Int, p1: InputStream?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun setAsciiStream(p0: Int, p1: InputStream?) {
        intentionallyNotImplemented()
    }

    override fun setUnicodeStream(p0: Int, p1: InputStream?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun setBinaryStream(p0: Int, p1: InputStream?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun setBinaryStream(p0: Int, p1: InputStream?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun setBinaryStream(p0: Int, p1: InputStream?) {
        intentionallyNotImplemented()
    }

    override fun clearParameters() {
        intentionallyNotImplemented()
    }

    override fun setObject(p0: Int, p1: Any?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun setObject(parameterIndex: Int, x: Any?) {
        when (x) {
            null -> params[parameterIndex - 1] = JsonNull
            is Int -> setInt(parameterIndex, x)
            is Long -> setLong(parameterIndex, x)
            is String -> setString(parameterIndex, x)
            else -> throw SQLFeatureNotSupportedException("setObject is not implemented for ${x::class}")
        }
    }

    override fun setObject(p0: Int, p1: Any?, p2: Int, p3: Int) {
        intentionallyNotImplemented()
    }

    override fun setCharacterStream(p0: Int, p1: Reader?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun setCharacterStream(p0: Int, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun setCharacterStream(p0: Int, p1: Reader?) {
        intentionallyNotImplemented()
    }

    override fun setRef(p0: Int, p1: Ref?) {
        intentionallyNotImplemented()
    }

    override fun setBlob(p0: Int, p1: Blob?) {
        intentionallyNotImplemented()
    }

    override fun setBlob(p0: Int, p1: InputStream?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun setBlob(p0: Int, p1: InputStream?) {
        intentionallyNotImplemented()
    }

    override fun setClob(p0: Int, p1: Clob?) {
        intentionallyNotImplemented()
    }

    override fun setClob(p0: Int, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun setClob(p0: Int, p1: Reader?) {
        intentionallyNotImplemented()
    }

    override fun setArray(p0: Int, p1: Array?) {
        intentionallyNotImplemented()
    }

    override fun getMetaData(): ResultSetMetaData {
        intentionallyNotImplemented()
    }

    override fun setURL(p0: Int, p1: URL?) {
        intentionallyNotImplemented()
    }

    override fun getParameterMetaData(): ParameterMetaData {
        intentionallyNotImplemented()
    }

    override fun setRowId(p0: Int, p1: RowId?) {
        intentionallyNotImplemented()
    }

    override fun setNString(p0: Int, p1: String?) {
        intentionallyNotImplemented()
    }

    override fun setNCharacterStream(p0: Int, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun setNCharacterStream(p0: Int, p1: Reader?) {
        intentionallyNotImplemented()
    }

    override fun setNClob(p0: Int, p1: NClob?) {
        intentionallyNotImplemented()
    }

    override fun setNClob(p0: Int, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun setNClob(p0: Int, p1: Reader?) {
        intentionallyNotImplemented()
    }

    override fun setSQLXML(p0: Int, p1: SQLXML?) {
        intentionallyNotImplemented()
    }
}