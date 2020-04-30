package processm.core.helpers.map2d

interface Map2D<Row, Column, Value> {

    /**
     * A view for a particular row/column, modifications are reflected in the original DoubleMap
     */
    interface View<K, Value> {
        operator fun get(k: K): Value?
        operator fun set(k: K, v: Value)
        val keys: Collection<K>
        fun toMap(): Map<K, Value> = keys.associateWith { get(it)!! }
    }

    operator fun get(row: Row, col: Column): Value?

    fun getRow(row: Row): View<Column, Value>

    fun getColumn(col: Column): View<Row, Value>

    operator fun set(row: Row, col: Column, v: Value)

    val rows: Collection<Row>

    val columns: Collection<Column>

    fun toMoM(): Map<Row, Map<Column, Value>> = rows.associateWith { row -> getRow(row).toMap() }
}