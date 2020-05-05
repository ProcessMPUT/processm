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

    override fun getDouble(columnLabel: String?): Double {
        i = labelToId[columnLabel]
        return if (i !== null) getDouble(i!!) else Double.NaN
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