package processm.core.helpers.map2d

/**
 * A 2D map, to replace awkward constructions such as HashMap of HashMaps.
 * The default implementation is [DoublingMap2D].
 */
interface Map2D<Row, Column, Value> {

    /**
     * A view for a particular row/column, modifications are reflected in the original Map2D.
     * Implementing full [MutableMap] has a potential to be very complex due to the possibly intricate insertion semantics
     */
    interface View<K, Value> : Map<K, Value> {
        operator fun set(k: K, v: Value)
    }

    /**
     * Returns value stored for ([row], [column]) key or null if there is no such value
     */
    operator fun get(row: Row, column: Column): Value?

    /**
     * Returns a view for [row]. The changes to the view are backed to the map.
     */
    fun getRow(row: Row): View<Column, Value>

    /**
     * Returns a view for [column]. The changes to the view are backed to the map.
     */
    fun getColumn(column: Column): View<Row, Value>

    /**
     * Updates the value stored for ([row], [column]).
     *
     * Observe that [value] is of non-nullable type, so one cannot remove an entry this way.
     */
    operator fun set(row: Row, column: Column, value: Value)

    /**
     * Removes the entry for the specified column.
     *
     * Contract:
     * Views created before deletion are not updated due to the high cost of calculations.
     * Data consistency after the operation is not guaranteed!
     */
    fun removeColumn(column: Column)

    /**
     * Removes the entry for the specified row.
     *
     * Contract:
     * Views created before deletion are not updated due to the high cost of calculations.
     * Data consistency after the operation is not guaranteed!
     */
    fun removeRow(row: Row)

    /**
     * The set of all non-empty rows.
     */
    val rows: Set<Row>

    /**
     * The set of all non-empty columns.
     */
    val columns: Set<Column>
}