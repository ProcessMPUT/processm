package processm.experimental.helpers.map2d

import processm.core.helpers.map2d.Map2D

/**
 * This is a performance baseline, based by dense arrays of the size given by [nRows] and [nColumns]
 */
class DenseMap2D<Row, Column, Value>(val nRows: Int, val nColumns: Int) :
    Map2D<Row, Column, Value> {

    private val data = Array<Array<Any?>>(nRows) { Array<Any?>(nColumns) { null } }

    private val row2idx = HashMap<Row, Int>()
    private fun row(r: Row): Int = row2idx.getOrPut(r) { row2idx.size }

    private val col2idx = HashMap<Column, Int>()
    private fun col(c: Column): Int = col2idx.getOrPut(c) { col2idx.size }

    override fun get(row: Row, col: Column): Value? = data[row(row)][col(col)] as Value?

    override fun getRow(row: Row): Map2D.View<Column, Value> = object :
        Map2D.View<Column, Value> {

        private val rowidx = row(row)

        override fun get(k: Column): Value? = data[rowidx][col(k)] as Value?

        override fun set(k: Column, v: Value) {
            data[rowidx][col(k)] = v
        }

        override val keys: Set<Column> by lazy {
            col2idx.filterValues { data[rowidx][it] != null }.keys
        }
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
        private val colidx = col(col)

        override fun get(k: Row): Value? = data[row(k)][colidx] as Value?

        override fun set(k: Row, v: Value) {
            data[row(k)][colidx] = v
        }

        override val keys: Set<Row> by lazy {
            row2idx.filterValues { data[it][colidx] != null }.keys
        }
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

    override fun set(row: Row, col: Column, v: Value) {
        data[row(row)][col(col)] = v
    }

    override fun removeColumn(column: Column) {
        TODO("Not yet implemented")
    }

    override fun removeRow(row: Row) {
        TODO("Not yet implemented")
    }

    override val rows: Set<Row>
        get() = row2idx.keys
    override val columns: Set<Column>
        get() = col2idx.keys

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
