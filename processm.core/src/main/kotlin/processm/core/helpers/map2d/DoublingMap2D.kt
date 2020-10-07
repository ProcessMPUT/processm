package processm.core.helpers.map2d

/**
 * The default implementation of [Map2D], backed by two hashmaps of hashmaps: one from rows, to columns, to values, and the other from columns, to rows, to values.
 */
class DoublingMap2D<Row, Column, Value> : Map2D<Row, Column, Value> {

    private class View<K, V>(private val get: () -> MutableMap<K, V>?, private val update: (K, V) -> Unit) :
        Map2D.View<K, V> {

        /**
         * This is to ensure that we are able to read an empty row/column without actually inserting it to the map.
         */
        private var backend: MutableMap<K, V>? = null
            get() {
                if (field == null)
                    field = get()
                return field
            }

        override fun set(k: K, v: V) = update(k, v)

        override val entries: Set<Map.Entry<K, V>>
            get() = backend?.entries ?: emptySet()

        override val keys: Set<K>
            get() = backend?.keys ?: emptySet()

        override val size: Int
            get() = backend?.size ?: 0

        override val values: Collection<V>
            get() = backend?.values ?: emptyList()

        override fun containsKey(key: K): Boolean = backend?.containsKey(key) == true

        override fun containsValue(value: V): Boolean = backend?.containsValue(value) == true

        override fun get(key: K): V? = backend?.get(key)

        override fun isEmpty(): Boolean = backend?.isEmpty() != false
    }

    private val rcv = HashMap<Row, HashMap<Column, Value>>()

    private val crv = HashMap<Column, HashMap<Row, Value>>()

    override fun get(row: Row, column: Column): Value? = rcv[row]?.get(column)

    override fun getRow(row: Row): Map2D.View<Column, Value> =
        View({ rcv[row] }, { col, value -> set(row, col, value) })

    override fun getColumn(column: Column): Map2D.View<Row, Value> =
        View({ crv[column] }, { row, value -> set(row, column, value) })

    override fun set(row: Row, column: Column, value: Value) {
        rcv.computeIfAbsent(row) { HashMap() }[column] = value
        crv.computeIfAbsent(column) { HashMap() }[row] = value
    }

    override fun removeColumn(column: Column) {
        crv.remove(column)
        rcv.values.forEach { it.remove(column) }
    }

    override fun removeRow(row: Row) {
        rcv.remove(row)
        crv.values.forEach { it.remove(row) }
    }

    /**
     * Remove value from map.
     * Warning: This will not update already created views!
     */
    fun removeValue(row: Row, column: Column) {
        rcv.computeIfAbsent(row) { HashMap() }.remove(column)
        crv.computeIfAbsent(column) { HashMap() }.remove(row)
    }

    override val rows: Set<Row>
        get() = rcv.keys
    override val columns: Set<Column>
        get() = crv.keys
}