package processm.core.helpers.map2d

/**
 * Creates a new map by replacing or adding entries to this map from another [map].
 */
operator fun <Row, Column, Value> Map2D<Row, out Column, out Value>.plus(map: Map2D<Row, out Column, out Value>): Map2D<Row, Column, Value> =
    DoublingMap2D(this).apply { putAll(map) }
