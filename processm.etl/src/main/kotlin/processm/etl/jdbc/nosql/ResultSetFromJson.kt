package processm.etl.jdbc.nosql

import kotlinx.serialization.json.*
import processm.helpers.mapToArray
import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.net.URL
import java.sql.*
import java.sql.Array
import java.sql.Date
import java.util.*


/**
 * Maps the given collection [docs] of JSON objects as a [ResultSet]. Vastly incomplete, offers only the bare minimum
 * for continuous queries to work
 */
class ResultSetFromJson(val docs: List<JsonElement>, columns: List<JsonPath>? = null, types: List<JDBCType>? = null) :
    ResultSet {

    private val JsonElement.jsonPrimitiveOrNull: JsonPrimitive?
        get() = this as? JsonPrimitive

    private fun JsonElement.keys(): List<List<String>> =
        when (this) {
            is JsonPrimitive -> listOf(emptyList())
            is JsonObject -> flatMap { e -> e.value.keys().map { suffix -> listOf(e.key) + suffix } }
            is JsonArray -> flatMapIndexed { idx, e -> e.keys().map { suffix -> listOf(idx.toString()) + suffix } }
            else -> throw IllegalArgumentException()
        }

    private operator fun JsonElement.get(path: List<String>): JsonPrimitive? {
        var element = this
        for (item in path) {
            element = if (element is JsonArray) {
                val v = item.toInt()
                if (v < element.size)
                    element[v]
                else
                    return null
            } else if (element is JsonObject) element[item] ?: return null
            else return null
        }
        return element as? JsonPrimitive
    }

    private fun guessType(c: JsonPath): JDBCType {
        var isLong = 0
        var isBool = 0
        var isDouble = 0
        var isNull = 0
        var isString = 0
        var isArray = 0
        var isObject = 0
        for (doc in docs) {
            when (doc[c]) {
                is JsonObject -> isObject++
                is JsonArray -> isArray++
                else -> {
                    val p = doc[c] as? JsonPrimitive
                    when {
                        p == null -> isNull++
                        p.longOrNull != null -> isLong++
                        p.booleanOrNull != null -> isBool++
                        p.doubleOrNull != null -> isDouble++
                        else -> isString++
                    }
                }
            }
        }
        return when {
            isString > 0 -> JDBCType.VARCHAR
            isArray > 0 -> JDBCType.ARRAY
            isObject > 0 -> JDBCType.OTHER
            isDouble > 0 -> JDBCType.DOUBLE
            isBool > 0 -> JDBCType.BOOLEAN
            isLong > 0 -> JDBCType.BIGINT
            else -> JDBCType.NULL
        }
    }

    private val columns: kotlin.Array<JsonPath>
    private val types: kotlin.Array<JDBCType>
    private var iterator: Int = -1
    private var wasNull: Boolean = false

    init {
        require(types == null || columns !== null) // if columns is not null types must be not null as well
        // first to set, then to array to ensure a) we don't have duplicates; b) order is fixed; c) we have index-based access.
        this.columns =
            (columns ?: docs.flatMapTo(HashSet()) { it.keys().map { path -> JsonPath(path) } }).toTypedArray()
        this.types = types?.toTypedArray() ?: this.columns.mapToArray(::guessType)
    }

    private fun intentionallyNotImplemented(): Nothing =
        throw SQLFeatureNotSupportedException("Intentionally not implemented")

    override fun <T : Any?> unwrap(p0: Class<T>?): T {
        intentionallyNotImplemented()
    }

    override fun isWrapperFor(p0: Class<*>?): Boolean {
        intentionallyNotImplemented()
    }

    override fun close() {
    }

    override fun next(): Boolean {
        iterator++
        return iterator < docs.size
    }

    override fun wasNull(): Boolean = wasNull

    override fun getString(p0: Int): String = getObject(p0).toString()

    override fun getString(p0: String): String = getObject(p0).toString()

    override fun getBoolean(p0: Int): Boolean = (getObject(p0) as? Boolean) ?: false

    override fun getBoolean(p0: String): Boolean = (getObject(p0) as? Boolean) ?: false

    override fun getByte(p0: Int): Byte = (getObject(p0) as? Long)?.toByte() ?: 0

    override fun getByte(p0: String): Byte = (getObject(p0) as? Long)?.toByte() ?: 0

    override fun getShort(p0: Int): Short = (getObject(p0) as? Long)?.toShort() ?: 0

    override fun getShort(p0: String): Short = (getObject(p0) as? Long)?.toShort() ?: 0

    override fun getInt(p0: Int): Int = (getObject(p0) as? Long)?.toInt() ?: 0

    override fun getInt(p0: String): Int = (getObject(p0) as? Long)?.toInt() ?: 0

    override fun getLong(p0: Int): Long = (getObject(p0) as? Long) ?: 0L

    override fun getLong(p0: String): Long = (getObject(p0) as? Long) ?: 0L

    override fun getFloat(p0: Int): Float = (getObject(p0) as? Double)?.toFloat() ?: 0.0f

    override fun getFloat(p0: String): Float = (getObject(p0) as? Double)?.toFloat() ?: 0.0f

    override fun getDouble(p0: Int): Double = (getObject(p0) as? Double) ?: 0.0

    override fun getDouble(p0: String): Double = (getObject(p0) as? Double) ?: 0.0

    override fun getBigDecimal(p0: Int, p1: Int): BigDecimal {
        intentionallyNotImplemented()
    }

    override fun getBigDecimal(p0: String?, p1: Int): BigDecimal {
        intentionallyNotImplemented()
    }

    override fun getBigDecimal(p0: Int): BigDecimal {
        intentionallyNotImplemented()
    }

    override fun getBigDecimal(p0: String?): BigDecimal {
        intentionallyNotImplemented()
    }

    override fun getBytes(p0: Int): ByteArray {
        intentionallyNotImplemented()
    }

    override fun getBytes(p0: String?): ByteArray {
        intentionallyNotImplemented()
    }

    override fun getDate(p0: Int): Date {
        intentionallyNotImplemented()
    }

    override fun getDate(p0: String?): Date {
        intentionallyNotImplemented()
    }

    override fun getDate(p0: Int, p1: Calendar?): Date {
        intentionallyNotImplemented()
    }

    override fun getDate(p0: String?, p1: Calendar?): Date {
        intentionallyNotImplemented()
    }

    override fun getTime(p0: Int): Time {
        intentionallyNotImplemented()
    }

    override fun getTime(p0: String?): Time {
        intentionallyNotImplemented()
    }

    override fun getTime(p0: Int, p1: Calendar?): Time {
        intentionallyNotImplemented()
    }

    override fun getTime(p0: String?, p1: Calendar?): Time {
        intentionallyNotImplemented()
    }

    override fun getTimestamp(p0: Int): Timestamp {
        intentionallyNotImplemented()
    }

    override fun getTimestamp(p0: String?): Timestamp {
        intentionallyNotImplemented()
    }

    override fun getTimestamp(p0: Int, p1: Calendar?): Timestamp {
        intentionallyNotImplemented()
    }

    override fun getTimestamp(p0: String?, p1: Calendar?): Timestamp {
        intentionallyNotImplemented()
    }

    override fun getAsciiStream(p0: Int): InputStream {
        intentionallyNotImplemented()
    }

    override fun getAsciiStream(p0: String?): InputStream {
        intentionallyNotImplemented()
    }

    override fun getUnicodeStream(p0: Int): InputStream {
        intentionallyNotImplemented()
    }

    override fun getUnicodeStream(p0: String?): InputStream {
        intentionallyNotImplemented()
    }

    override fun getBinaryStream(p0: Int): InputStream {
        intentionallyNotImplemented()
    }

    override fun getBinaryStream(p0: String?): InputStream {
        intentionallyNotImplemented()
    }

    override fun getWarnings(): SQLWarning {
        intentionallyNotImplemented()
    }

    override fun clearWarnings() {
        intentionallyNotImplemented()
    }

    override fun getCursorName(): String {
        intentionallyNotImplemented()
    }

    override fun getMetaData(): ResultSetMetaData = object : ResultSetMetaData {
        override fun <T : Any?> unwrap(p0: Class<T>?): T {
            intentionallyNotImplemented()
        }

        override fun isWrapperFor(p0: Class<*>?): Boolean {
            intentionallyNotImplemented()
        }

        override fun getColumnCount(): Int = columns.size

        override fun isAutoIncrement(p0: Int): Boolean {
            intentionallyNotImplemented()
        }

        override fun isCaseSensitive(p0: Int): Boolean {
            intentionallyNotImplemented()
        }

        override fun isSearchable(p0: Int): Boolean {
            intentionallyNotImplemented()
        }

        override fun isCurrency(p0: Int): Boolean {
            intentionallyNotImplemented()
        }

        override fun isNullable(p0: Int): Int {
            intentionallyNotImplemented()
        }

        override fun isSigned(p0: Int): Boolean {
            intentionallyNotImplemented()
        }

        override fun getColumnDisplaySize(p0: Int): Int {
            intentionallyNotImplemented()
        }

        override fun getColumnLabel(columntIndex: Int): String = getColumnLabel(columntIndex)

        override fun getColumnName(columnIndex: Int): String = columns[columnIndex - 1].toString()

        override fun getSchemaName(p0: Int): String {
            intentionallyNotImplemented()
        }

        override fun getPrecision(p0: Int): Int {
            intentionallyNotImplemented()
        }

        override fun getScale(p0: Int): Int {
            intentionallyNotImplemented()
        }

        override fun getTableName(p0: Int): String {
            intentionallyNotImplemented()
        }

        override fun getCatalogName(p0: Int): String {
            intentionallyNotImplemented()
        }

        override fun getColumnType(columnIndex: Int): Int = types[columnIndex - 1].vendorTypeNumber

        override fun getColumnTypeName(columnIndex: Int): String = types[columnIndex - 1].name

        override fun isReadOnly(p0: Int): Boolean = true

        override fun isWritable(p0: Int): Boolean = false

        override fun isDefinitelyWritable(p0: Int): Boolean = false

        override fun getColumnClassName(p0: Int): String {
            intentionallyNotImplemented()
        }

    }

    override fun getObject(columnIndex: Int): Any? {
        val realColumnIndex = columnIndex - 1
        return when (types[realColumnIndex]) {
            JDBCType.NULL -> null
            JDBCType.BIGINT -> docs[iterator][columns[realColumnIndex]]?.jsonPrimitiveOrNull?.longOrNull
            JDBCType.BOOLEAN -> docs[iterator][columns[realColumnIndex]]?.jsonPrimitiveOrNull?.booleanOrNull
            JDBCType.DOUBLE -> docs[iterator][columns[realColumnIndex]]?.jsonPrimitiveOrNull?.doubleOrNull
            JDBCType.VARCHAR -> docs[iterator][columns[realColumnIndex]]?.jsonPrimitiveOrNull?.contentOrNull
            JDBCType.ARRAY -> docs[iterator][columns[realColumnIndex]] as? JsonArray
            JDBCType.OTHER -> docs[iterator][columns[realColumnIndex]]
            else -> throw SQLException()
        }.also { wasNull = (it == null) }
    }

    override fun getObject(columnLabel: String): Any? = getObject(findColumn(columnLabel))

    override fun getObject(p0: Int, p1: MutableMap<String, Class<*>>?): Any {
        intentionallyNotImplemented()
    }

    override fun getObject(p0: String?, p1: MutableMap<String, Class<*>>?): Any {
        intentionallyNotImplemented()
    }

    override fun <T : Any?> getObject(p0: Int, p1: Class<T>?): T {
        intentionallyNotImplemented()
    }

    override fun <T : Any?> getObject(p0: String?, p1: Class<T>?): T {
        intentionallyNotImplemented()
    }

    override fun findColumn(columnLabel: String): Int {
        val idx = columns.indexOf(JsonPath(columnLabel))
        if (idx >= 0)
            return idx + 1
        else
            throw SQLException()
    }

    override fun getCharacterStream(p0: Int): Reader {
        intentionallyNotImplemented()
    }

    override fun getCharacterStream(p0: String?): Reader {
        intentionallyNotImplemented()
    }

    override fun isBeforeFirst(): Boolean = iterator < 0

    override fun isAfterLast(): Boolean = iterator >= docs.size

    override fun isFirst(): Boolean = iterator == 0

    override fun isLast(): Boolean = iterator == docs.size - 1

    override fun beforeFirst() {
        iterator = -1
    }

    override fun afterLast() {
        iterator = docs.size
    }

    override fun first(): Boolean {
        iterator = 0
        return iterator in docs.indices
    }

    override fun last(): Boolean {
        iterator = docs.size - 1
        return iterator in docs.indices
    }

    override fun getRow(): Int = iterator + 1

    override fun absolute(row: Int): Boolean {
        iterator = when {
            row < 0 -> (docs.size + row).coerceAtLeast(-1)
            row == 0 -> -1
            else -> (row - 1).coerceAtMost(docs.size)
        }
        return iterator in docs.indices
    }

    override fun relative(rows: Int): Boolean {
        iterator = (iterator + rows).coerceAtLeast(-1).coerceAtMost(docs.size)
        return iterator in docs.indices
    }

    override fun previous(): Boolean {
        intentionallyNotImplemented()
    }

    override fun setFetchDirection(p0: Int) {
        intentionallyNotImplemented()
    }

    override fun getFetchDirection(): Int {
        intentionallyNotImplemented()
    }

    override fun setFetchSize(p0: Int) {
        intentionallyNotImplemented()
    }

    override fun getFetchSize(): Int {
        intentionallyNotImplemented()
    }

    override fun getType(): Int {
        intentionallyNotImplemented()
    }

    override fun getConcurrency(): Int {
        intentionallyNotImplemented()
    }

    override fun rowUpdated(): Boolean {
        intentionallyNotImplemented()
    }

    override fun rowInserted(): Boolean {
        intentionallyNotImplemented()
    }

    override fun rowDeleted(): Boolean {
        intentionallyNotImplemented()
    }

    override fun updateNull(p0: Int) {
        intentionallyNotImplemented()
    }

    override fun updateNull(p0: String?) {
        intentionallyNotImplemented()
    }

    override fun updateBoolean(p0: Int, p1: Boolean) {
        intentionallyNotImplemented()
    }

    override fun updateBoolean(p0: String?, p1: Boolean) {
        intentionallyNotImplemented()
    }

    override fun updateByte(p0: Int, p1: Byte) {
        intentionallyNotImplemented()
    }

    override fun updateByte(p0: String?, p1: Byte) {
        intentionallyNotImplemented()
    }

    override fun updateShort(p0: Int, p1: Short) {
        intentionallyNotImplemented()
    }

    override fun updateShort(p0: String?, p1: Short) {
        intentionallyNotImplemented()
    }

    override fun updateInt(p0: Int, p1: Int) {
        intentionallyNotImplemented()
    }

    override fun updateInt(p0: String?, p1: Int) {
        intentionallyNotImplemented()
    }

    override fun updateLong(p0: Int, p1: Long) {
        intentionallyNotImplemented()
    }

    override fun updateLong(p0: String?, p1: Long) {
        intentionallyNotImplemented()
    }

    override fun updateFloat(p0: Int, p1: Float) {
        intentionallyNotImplemented()
    }

    override fun updateFloat(p0: String?, p1: Float) {
        intentionallyNotImplemented()
    }

    override fun updateDouble(p0: Int, p1: Double) {
        intentionallyNotImplemented()
    }

    override fun updateDouble(p0: String?, p1: Double) {
        intentionallyNotImplemented()
    }

    override fun updateBigDecimal(p0: Int, p1: BigDecimal?) {
        intentionallyNotImplemented()
    }

    override fun updateBigDecimal(p0: String?, p1: BigDecimal?) {
        intentionallyNotImplemented()
    }

    override fun updateString(p0: Int, p1: String?) {
        intentionallyNotImplemented()
    }

    override fun updateString(p0: String?, p1: String?) {
        intentionallyNotImplemented()
    }

    override fun updateBytes(p0: Int, p1: ByteArray?) {
        intentionallyNotImplemented()
    }

    override fun updateBytes(p0: String?, p1: ByteArray?) {
        intentionallyNotImplemented()
    }

    override fun updateDate(p0: Int, p1: Date?) {
        intentionallyNotImplemented()
    }

    override fun updateDate(p0: String?, p1: Date?) {
        intentionallyNotImplemented()
    }

    override fun updateTime(p0: Int, p1: Time?) {
        intentionallyNotImplemented()
    }

    override fun updateTime(p0: String?, p1: Time?) {
        intentionallyNotImplemented()
    }

    override fun updateTimestamp(p0: Int, p1: Timestamp?) {
        intentionallyNotImplemented()
    }

    override fun updateTimestamp(p0: String?, p1: Timestamp?) {
        intentionallyNotImplemented()
    }

    override fun updateAsciiStream(p0: Int, p1: InputStream?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun updateAsciiStream(p0: String?, p1: InputStream?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun updateAsciiStream(p0: Int, p1: InputStream?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateAsciiStream(p0: String?, p1: InputStream?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateAsciiStream(p0: Int, p1: InputStream?) {
        intentionallyNotImplemented()
    }

    override fun updateAsciiStream(p0: String?, p1: InputStream?) {
        intentionallyNotImplemented()
    }

    override fun updateBinaryStream(p0: Int, p1: InputStream?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun updateBinaryStream(p0: String?, p1: InputStream?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun updateBinaryStream(p0: Int, p1: InputStream?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateBinaryStream(p0: String?, p1: InputStream?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateBinaryStream(p0: Int, p1: InputStream?) {
        intentionallyNotImplemented()
    }

    override fun updateBinaryStream(p0: String?, p1: InputStream?) {
        intentionallyNotImplemented()
    }

    override fun updateCharacterStream(p0: Int, p1: Reader?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun updateCharacterStream(p0: String?, p1: Reader?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun updateCharacterStream(p0: Int, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateCharacterStream(p0: String?, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateCharacterStream(p0: Int, p1: Reader?) {
        intentionallyNotImplemented()
    }

    override fun updateCharacterStream(p0: String?, p1: Reader?) {
        intentionallyNotImplemented()
    }

    override fun updateObject(p0: Int, p1: Any?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun updateObject(p0: Int, p1: Any?) {
        intentionallyNotImplemented()
    }

    override fun updateObject(p0: String?, p1: Any?, p2: Int) {
        intentionallyNotImplemented()
    }

    override fun updateObject(p0: String?, p1: Any?) {
        intentionallyNotImplemented()
    }

    override fun insertRow() {
        intentionallyNotImplemented()
    }

    override fun updateRow() {
        intentionallyNotImplemented()
    }

    override fun deleteRow() {
        intentionallyNotImplemented()
    }

    override fun refreshRow() {
        intentionallyNotImplemented()
    }

    override fun cancelRowUpdates() {
        intentionallyNotImplemented()
    }

    override fun moveToInsertRow() {
        intentionallyNotImplemented()
    }

    override fun moveToCurrentRow() {
        intentionallyNotImplemented()
    }

    override fun getStatement(): Statement {
        intentionallyNotImplemented()
    }

    override fun getRef(p0: Int): Ref {
        intentionallyNotImplemented()
    }

    override fun getRef(p0: String?): Ref {
        intentionallyNotImplemented()
    }

    override fun getBlob(p0: Int): Blob {
        intentionallyNotImplemented()
    }

    override fun getBlob(p0: String?): Blob {
        intentionallyNotImplemented()
    }

    override fun getClob(p0: Int): Clob {
        intentionallyNotImplemented()
    }

    override fun getClob(p0: String?): Clob {
        intentionallyNotImplemented()
    }

    override fun getArray(p0: Int): Array {
        intentionallyNotImplemented()
    }

    override fun getArray(p0: String?): Array {
        intentionallyNotImplemented()
    }

    override fun getURL(p0: Int): URL {
        intentionallyNotImplemented()
    }

    override fun getURL(p0: String?): URL {
        intentionallyNotImplemented()
    }

    override fun updateRef(p0: Int, p1: Ref?) {
        intentionallyNotImplemented()
    }

    override fun updateRef(p0: String?, p1: Ref?) {
        intentionallyNotImplemented()
    }

    override fun updateBlob(p0: Int, p1: Blob?) {
        intentionallyNotImplemented()
    }

    override fun updateBlob(p0: String?, p1: Blob?) {
        intentionallyNotImplemented()
    }

    override fun updateBlob(p0: Int, p1: InputStream?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateBlob(p0: String?, p1: InputStream?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateBlob(p0: Int, p1: InputStream?) {
        intentionallyNotImplemented()
    }

    override fun updateBlob(p0: String?, p1: InputStream?) {
        intentionallyNotImplemented()
    }

    override fun updateClob(p0: Int, p1: Clob?) {
        intentionallyNotImplemented()
    }

    override fun updateClob(p0: String?, p1: Clob?) {
        intentionallyNotImplemented()
    }

    override fun updateClob(p0: Int, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateClob(p0: String?, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateClob(p0: Int, p1: Reader?) {
        intentionallyNotImplemented()
    }

    override fun updateClob(p0: String?, p1: Reader?) {
        intentionallyNotImplemented()
    }

    override fun updateArray(p0: Int, p1: Array?) {
        intentionallyNotImplemented()
    }

    override fun updateArray(p0: String?, p1: Array?) {
        intentionallyNotImplemented()
    }

    override fun getRowId(p0: Int): RowId {
        intentionallyNotImplemented()
    }

    override fun getRowId(p0: String?): RowId {
        intentionallyNotImplemented()
    }

    override fun updateRowId(p0: Int, p1: RowId?) {
        intentionallyNotImplemented()
    }

    override fun updateRowId(p0: String?, p1: RowId?) {
        intentionallyNotImplemented()
    }

    override fun getHoldability(): Int {
        intentionallyNotImplemented()
    }

    override fun isClosed(): Boolean {
        intentionallyNotImplemented()
    }

    override fun updateNString(p0: Int, p1: String?) {
        intentionallyNotImplemented()
    }

    override fun updateNString(p0: String?, p1: String?) {
        intentionallyNotImplemented()
    }

    override fun updateNClob(p0: Int, p1: NClob?) {
        intentionallyNotImplemented()
    }

    override fun updateNClob(p0: String?, p1: NClob?) {
        intentionallyNotImplemented()
    }

    override fun updateNClob(p0: Int, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateNClob(p0: String?, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateNClob(p0: Int, p1: Reader?) {
        intentionallyNotImplemented()
    }

    override fun updateNClob(p0: String?, p1: Reader?) {
        intentionallyNotImplemented()
    }

    override fun getNClob(p0: Int): NClob {
        intentionallyNotImplemented()
    }

    override fun getNClob(p0: String?): NClob {
        intentionallyNotImplemented()
    }

    override fun getSQLXML(p0: Int): SQLXML {
        intentionallyNotImplemented()
    }

    override fun getSQLXML(p0: String?): SQLXML {
        intentionallyNotImplemented()
    }

    override fun updateSQLXML(p0: Int, p1: SQLXML?) {
        intentionallyNotImplemented()
    }

    override fun updateSQLXML(p0: String?, p1: SQLXML?) {
        intentionallyNotImplemented()
    }

    override fun getNString(p0: Int): String {
        intentionallyNotImplemented()
    }

    override fun getNString(p0: String?): String {
        intentionallyNotImplemented()
    }

    override fun getNCharacterStream(p0: Int): Reader {
        intentionallyNotImplemented()
    }

    override fun getNCharacterStream(p0: String?): Reader {
        intentionallyNotImplemented()
    }

    override fun updateNCharacterStream(p0: Int, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateNCharacterStream(p0: String?, p1: Reader?, p2: Long) {
        intentionallyNotImplemented()
    }

    override fun updateNCharacterStream(p0: Int, p1: Reader?) {
        intentionallyNotImplemented()
    }

    override fun updateNCharacterStream(p0: String?, p1: Reader?) {
        intentionallyNotImplemented()
    }

}
