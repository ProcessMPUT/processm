package processm.experimental.helpers.map2d

import processm.core.helpers.map2d.Map2D
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/**
 * A single hand-made hashmap with some code for combining hasesh. The performance is poor.
 */
class WheelReinventingMap2D<Row, Column, Value>(expected: Int) :
    Map2D<Row, Column, Value> {

    internal data class Node<Row, Column, Value>(val row: Row, val col: Column, var value: Value) {
        val colHash = col.hashCode()
        val rowHash = row.hashCode()
    }

    //<Row, ArrayList<Node<Row, Column, Value>>>
    internal lateinit var data: ArrayList<ArrayList<Node<Row, Column, Value>>>
    private val _rows = HashSet<Row>()
    private val _cols = HashSet<Column>()

    //    private var nmask: Int = 0
//    private var nshift: Int = 0
    private var nmod: Int = 0

    init {
        resize(expected)
    }

    private fun resize(_newn: Int) {
        nmod = _newn / 10
//        var nsqrt = 1
//        nshift = 0
//        while (nsqrt * nsqrt < _newn) {
//            nsqrt *= 2
//            nshift++
//        }
//        nmask = nsqrt - 1
        val newdata = ArrayList<ArrayList<Node<Row, Column, Value>>>()
//        for (i in 0 until nsqrt * nsqrt)
        for (i in 0 until nmod)
            newdata.add(ArrayList())
        if (this::data.isInitialized) {
            TODO()
        } else {
            data = newdata
        }
    }

    private fun indexrc(row: Int, col: Int): Int {
        val p = 87178291199L
        val r = (row.toLong() * Int.MAX_VALUE) % p
        val c = col.toLong()
//        val r = (((3 * row + 23) % p) and 0x7fff).toInt()
//        val c = (((5 * col + 29) % p) and 0x7fff).toInt()
        val k = r + c
        return (((7 * k + 31) % p) % nmod).toInt()
    }

    private fun slice(row: Row, col: Column) = data[indexrc(row.hashCode(), col.hashCode())]

    override fun get(row: Row, col: Column): Value? =
        filterSlice(slice(row, col), row.hashCode(), col.hashCode(), row, col)?.value

    private fun filterSlice(
        slice: ArrayList<Node<Row, Column, Value>>,
        rh: Int,
        ch: Int,
        row: Row,
        col: Column
    ): Node<Row, Column, Value>? {
        // Not using iterators on purpose: they seem to be much slower
        var i = 0
        while (i < slice.size) {
            val n = slice[i]
            if (n.rowHash == rh && n.colHash == ch && n.col == col && n.row == row)
                return n
            i++
        }
        return null
    }

    override fun getRow(row: Row): Map2D.View<Column, Value> = object :
        Map2D.View<Column, Value> {

//        private val h = (row.hashCode() and nmask) shl nshift
//
//        private fun slice(col: Column) = data[h or (col.hashCode() and nmask)]

        private val h = row.hashCode()
        private fun slice(col: Column) = data[indexrc(h, col.hashCode())]

        override fun get(col: Column): Value? = filterSlice(slice(col), h, col.hashCode(), row, col)?.value

        override fun set(col: Column, v: Value) = set(slice(col), row, col, v)

        override val keys
            get() = _cols.filter { col -> slice(col).any { it.row == row } }.toSet()
        override val entries: Set<Map.Entry<Column, Value>>
            get() = TODO("Not yet implemented")
        override val size: Int
            get() = TODO("Not yet implemented")
        override val values: Collection<Value>
            get() = TODO("Not yet implemented")

        override fun containsKey(key: Column): Boolean {
            TODO("Not yet implemented")
        }

        override fun containsValue(value: Value): Boolean {
            TODO("Not yet implemented")
        }

        override fun isEmpty(): Boolean {
            TODO("Not yet implemented")
        }

    }

    override fun getColumn(col: Column): Map2D.View<Row, Value> = object :
        Map2D.View<Row, Value> {

        private val ch = col.hashCode()

        //        private val h = (ch and nmask)
//
//        private fun slice(row: Row) = data[((row.hashCode() and nmask) shl nshift) or h]
        private fun slice(row: Row) = data[indexrc(row.hashCode(), ch)]

        override fun get(row: Row): Value? {
            val rh = row.hashCode()
            //val slice = data[((rh and nmask) shl nshift) or h]
            val slice = data[indexrc(rh, ch)]
            var i = 0
            while (i < slice.size) {
                val n = slice[i]
                if (n.colHash == ch && n.rowHash == rh && n.col == col && n.row == row)
                    return n.value
                i++
            }
            return null
        }

        override fun set(row: Row, v: Value) = set(slice(row), row, col, v)

        override val keys
            get() = _rows.filter { row -> slice(row).any { it.col == col } }.toSet()
        override val entries: Set<Map.Entry<Row, Value>>
            get() = TODO("Not yet implemented")
        override val size: Int
            get() = TODO("Not yet implemented")
        override val values: Collection<Value>
            get() = TODO("Not yet implemented")

        override fun containsKey(key: Row): Boolean {
            TODO("Not yet implemented")
        }

        override fun containsValue(value: Value): Boolean {
            TODO("Not yet implemented")
        }

        override fun isEmpty(): Boolean {
            TODO("Not yet implemented")
        }

    }

    override fun set(row: Row, col: Column, v: Value) = set(slice(row, col), row, col, v)

    private fun set(slice: ArrayList<Node<Row, Column, Value>>, row: Row, col: Column, v: Value) {
        val n = filterSlice(slice, row.hashCode(), col.hashCode(), row, col)
        if (n != null) {
            n.value = v
            return
        }
        _rows.add(row)
        _cols.add(col)
        slice.add(Node(row, col, v))
    }

    override fun removeColumn(column: Column) {
        TODO("Not yet implemented")
    }

    override fun removeRow(row: Row) {
        TODO("Not yet implemented")
    }

    override val rows
        get() = Collections.unmodifiableSet(_rows)

    override val columns
        get() = Collections.unmodifiableSet(_cols)

    override fun containsKeys(row: Row, column: Column): Boolean {
        TODO("Not yet implemented")
    }

    override fun clear() {
        TODO("Not yet implemented")
    }

    override fun compute(row: Row, column: Column, callback: (row: Row, col: Column, old: Value?) -> Value?) {
        TODO("Not yet implemented")
    }

    override val size: Int
        get() = TODO("Not yet implemented")
}
