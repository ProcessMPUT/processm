package processm.core.log.hierarchical

import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*
import kotlin.collections.HashMap

internal class ErrorSuppressingResultSet(private val backingResultSet: ResultSet) : ResultSet by backingResultSet {
    private val labelToId: MutableMap<String, Int>
    private var i: Int? = null

    init {
        with(backingResultSet.metaData) {
            labelToId = HashMap((this.columnCount shl 2) / 3)
            for (i in 1..this.columnCount)
                labelToId[getColumnLabel(i)] = i
        }
    }

    override fun getString(columnLabel: String?): String? {
        i = labelToId[columnLabel]
        return if (i !== null) getString(i!!) else null
    }

    override fun getBoolean(columnLabel: String?): Boolean {
        i = labelToId[columnLabel]
        return if (i !== null) getBoolean(i!!) else false
    }

    override fun getDouble(columnLabel: String?): Double {
        i = labelToId[columnLabel]
        return if (i !== null) getDouble(i!!) else Double.NaN
    }

    override fun getLong(columnLabel: String?): Long {
        i = labelToId[columnLabel]
        return if (i !== null) getLong(i!!) else 0L
    }

    override fun getInt(columnLabel: String?): Int {
        i = labelToId[columnLabel]
        return if (i !== null) getInt(i!!) else 0
    }

    override fun getTimestamp(columnLabel: String?, cal: Calendar): Timestamp? {
        i = labelToId[columnLabel]
        return if (i !== null) getTimestamp(i!!, cal) else null
    }

    override fun wasNull(): Boolean {
        return i === null || backingResultSet.wasNull()
    }
}

internal fun ResultSet.getDoubleOrNull(columnLabel: String): Double? {
    val d = this.getDouble(columnLabel)
    return if (this.wasNull()) null else d
}

internal fun ResultSet.getDoubleOrNull(columnIndex: Int): Double? {
    val d = this.getDouble(columnIndex)
    return if (this.wasNull()) null else d
}

internal fun ResultSet.getLongOrNull(columnLabel: String): Long? {
    val l = this.getLong(columnLabel)
    return if (this.wasNull()) null else l
}

internal fun ResultSet.getLongOrNull(columnIndex: Int): Long? {
    val l = this.getLong(columnIndex)
    return if (this.wasNull()) null else l
}

internal fun ResultSet.getIntOrNull(columnLabel: String): Int? {
    val i = this.getInt(columnLabel)
    return if (this.wasNull()) null else i
}

internal fun ResultSet.getIntOrNull(columnIndex: Int): Int? {
    val i = this.getInt(columnIndex)
    return if (this.wasNull()) null else i
}

internal fun ResultSet.getBooleanOrNull(columnLabel: String): Boolean? {
    val b = this.getBoolean(columnLabel)
    return if (this.wasNull()) null else b
}

internal fun ResultSet.getBooleanOrNull(columnIndex: Int): Boolean? {
    val b = this.getBoolean(columnIndex)
    return if (this.wasNull()) null else b
}