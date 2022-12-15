package processm.core.log.hierarchical

import java.sql.ResultSet

/**
 * A wrapper for a [ResultSet] sorting the rows according to the order given in `ids`, using the column number
 * `columnIndex` as the sorting key. Since the rows are not cached, [base] must be a result set capable of scrolling (i.e.,
 * [ResultSet.TYPE_SCROLL_SENSITIVE] or [ResultSet.TYPE_SCROLL_INSENSITIVE]).
 */
internal class SortingResultSet(private val base: ResultSet, ids: List<Long>, columnIndex: Int = 2) :
    ResultSet by base {

    private val order: List<Int>
    private var current: Int = 0
        set(value) {
            if (value == 0) {
                base.beforeFirst()
                field = 0
            } else if (value > order.size) {
                base.afterLast()
                field = order.size + 1
            } else if (value > 0) {
                base.absolute(order[value - 1])
                field = value
            } else {
                assert(value < 0)
                field = value % order.size + 1
                base.absolute(order[field - 1])
            }
        }

    init {
        require(base.type == ResultSet.TYPE_SCROLL_INSENSITIVE || base.type == ResultSet.TYPE_SCROLL_SENSITIVE)
        val tmp = ArrayList<Pair<Int, Int>>()
        val indices = ids.withIndex().associate { it.value to it.index }
        while (base.next()) {
            val id = base.getLong(columnIndex)
            tmp.add(indices[id]!! to base.row)
        }
        tmp.sortBy { ids.first() }
        order = tmp.map { it.second }
        assert(order.all { it > 0 })
    }

    override fun isAfterLast(): Boolean = current == order.size + 1

    override fun isBeforeFirst(): Boolean = current == 0

    override fun isFirst(): Boolean = current == 1

    override fun isLast(): Boolean = current == order.size

    override fun beforeFirst() {
        current = 0
    }

    override fun afterLast() {
        current = order.size + 1
    }

    override fun first(): Boolean {
        current = 1
        return order.isNotEmpty()
    }

    override fun last(): Boolean {
        current = order.size
        return order.isNotEmpty()
    }

    override fun next(): Boolean {
        current++
        return !isAfterLast
    }

    override fun absolute(row: Int): Boolean {
        current = row
        return current in 1..order.size
    }

    override fun getRow(): Int {
        return current
    }
}