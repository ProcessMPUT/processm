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
     * Inserts all items of the given [other] map into this map, possibly overwriting the current values.
     */
    fun putAll(other: Map2D<Row, out Column, out Value>) {
        for (row in other.rows) {
            for ((col, v) in other.getRow(row))
                set(row, col, v)
        }
    }

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
     * Remove value from map.
     * Contract: Calling this method invalidates the previously instantiated views.
     */
    fun removeValue(row: Row, column: Column)

    /**
     * Removes all entries from this map.
     */
    fun clear()

    /**
     * Computes new value for the given [row] and [column] based on the [callback] function. The callback function
     * accepts the [row], [column], and [old] value. [old] attains the value of null if the previous value does not exist.
     * The value returned by the callback function replaces the [old] value unless null. For null, the value is removed.
     *
     * @return the new value associated with the specified key, or null if none
     */
    fun compute(row: Row, column: Column, callback: (row: Row, col: Column, old: Value?) -> Value?): Value?

    /**
     * Maps the values in this map 2D using the given [func] function into a new map 2D.
     * @return The new map 2D with the values mapped.
     */
    fun <R> mapValues(func: (row: Row, col: Column, old: Value) -> R): Map2D<in Row, in Column, in R> = TODO()

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

    /**
     * All values stored in the map, in arbitrary order.
     * A direct counterpart of [Map.values], although without the [Collection] interface to avoid unnecessary overhead
     */
    val values: Sequence<Value>
        get() = rows.asSequence().flatMap { getRow(it).values.asSequence() }
}
