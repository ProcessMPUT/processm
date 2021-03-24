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
     * Returns true if the element in the given [row] and [column] exists.
     */
    fun containsKeys(row: Row, column: Column): Boolean

    /**
     * Determines whether this map is empty.
     */
    fun isEmpty(): Boolean = size == 0

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
     * Calling this method invalidates the previously instantiated views.
     */
    fun removeColumn(column: Column)

    /**
     * Removes the entry for the specified row.
     *
     * Contract:
     * Calling this method invalidates the previously instantiated views.
     */
    fun removeRow(row: Row)

    /**
     * Removes all entries from this map.
     */
    fun clear()

    /**
     * Computes new value for the given [row] and [column] based on the [callback] function. The callback function
     * accept the [row], [column], and [old] value. [old] attains the value of null if the previous value does not exist.
     * The value returned by the callback function replaces the [old] value unless null. For null, the value is removed.
     */
    fun compute(row: Row, column: Column, callback: (row: Row, col: Column, old: Value?) -> Value?)

    /**
     * The set of all non-empty rows.
     */
    val rows: Set<Row>

    /**
     * The set of all non-empty columns.
     */
    val columns: Set<Column>

    /**
     * The total number of items in this map.
     */
    val size: Int
}
