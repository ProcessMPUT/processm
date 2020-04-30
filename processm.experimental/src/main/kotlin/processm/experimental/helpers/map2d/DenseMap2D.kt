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

    }

    override fun set(row: Row, col: Column, v: Value) {
        data[row(row)][col(col)] = v
    }

    override val rows: Set<Row>
        get() = row2idx.keys
    override val columns: Set<Column>
        get() = col2idx.keys
}