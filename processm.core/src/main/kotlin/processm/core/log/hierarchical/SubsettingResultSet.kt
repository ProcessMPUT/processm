package processm.core.log.hierarchical

import java.sql.ResultSet

internal class SubsettingResultSet(private val base: ResultSet, private val predicates: List<(ResultSet) -> Boolean>) :
    ResultSet by base {

    constructor(base: ResultSet, ids: List<Long>, columntIndex: Int) :
            this(base, ids.map { id -> { rs -> rs.getLong(columntIndex) == id } })

    private var current = -1
    private var row = 0
    override fun getType(): Int = ResultSet.TYPE_FORWARD_ONLY

    override fun absolute(row: Int): Boolean = throw UnsupportedOperationException()

    override fun close() {
        if (base.isAfterLast)
            base.close()
    }

    override fun isAfterLast(): Boolean = current == predicates.size

    override fun isBeforeFirst(): Boolean = current < 0

    override fun getRow(): Int = row

    override fun next(): Boolean {
        if (current >= 0) {
            if (!base.next()) {
                current = predicates.size
                return false
            }
        } else {
            current = 0
            if (base.isBeforeFirst)
                if (!base.next()) {
                    current = predicates.size
                    return false
                }
            if (base.row == 0) {
                current = predicates.size
                return false
            }
        }
        if (base.isAfterLast) {
            current = predicates.size
            return false
        }
        assert(!base.isBeforeFirst && !base.isAfterLast)
        while (current < predicates.size) {
            if (predicates[current](base)) {
                println("Arrived at ${getObject(1)} ${getObject(2)}")
                row++
                return true
            }
            current++
        }
        return false
    }
}