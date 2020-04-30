package processm.core.helpers.map2d

class DoublingMap2D<Row, Column, Value> : Map2D<Row, Column, Value> {

    private class View<K, V>(private val data: Map<K, V>, private val update: (K, V) -> Unit) : Map2D.View<K, V> {
        override fun get(k: K): V? = data[k]

        override fun set(k: K, v: V) = update(k, v)

        override val keys: Set<K>
            get() = data.keys
    }

    private val rcv = HashMap<Row, HashMap<Column, Value>>()

    private val crv = HashMap<Column, HashMap<Row, Value>>()

    override fun get(row: Row, col: Column): Value? = rcv[row]?.get(col)

    override fun getRow(row: Row): Map2D.View<Column, Value> = View(rcv[row].orEmpty()) { col, value -> set(row, col, value) }

    override fun getColumn(col: Column): Map2D.View<Row, Value> = View(crv[col].orEmpty()) { row, value -> set(row, col, value) }

    override fun set(row: Row, col: Column, v: Value) {
        rcv.getOrPut(row) { HashMap() }[col] = v
        crv.getOrPut(col) { HashMap() }[row] = v
    }

    override val rows: Set<Row>
        get() = rcv.keys
    override val columns: Set<Column>
        get() = crv.keys

}