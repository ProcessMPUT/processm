package processm.experimental.helpers.map2d

import processm.core.helpers.map2d.Map2D


/**
 * The main purpose of this class was to define semantics of [Map2D].
 * After selecting [DoublingMap2D] as the implementation, [WrappingMap2D] is left here just in case.
 */
class WrappingMap2D<Row, Column, Value>(private val data: MutableMap<Pair<Row, Column>, Value> = HashMap()) :
    Map2D<Row, Column, Value> {

    private class RowView<Row, Column, Value>(val base: WrappingMap2D<Row, Column, Value>, val row: Row) :
        Map2D.View<Column, Value> {
        override fun get(col: Column): Value? = base[row, col]

        override fun set(col: Column, v: Value) = base.set(row, col, v)

        override val keys: Set<Column>
            get() = base.data.keys.filter { it.first == row }.map { it.second }.toSet()
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

    private class ColumnView<Row, Column, Value>(val base: WrappingMap2D<Row, Column, Value>, val col: Column) :
        Map2D.View<Row, Value> {
        override fun get(row: Row): Value? = base[row, col]

        override fun set(row: Row, v: Value) = base.set(row, col, v)

        override val keys: Set<Row>
            get() = base.data.keys.filter { it.second == col }.map { it.first }.toSet()
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

    override fun get(row: Row, col: Column): Value? = data[row to col]

    override fun getRow(row: Row): Map2D.View<Column, Value> =
        RowView(this, row)

    override fun getColumn(col: Column): Map2D.View<Row, Value> =
        ColumnView(this, col)

    override fun set(row: Row, col: Column, v: Value) = data.set(row to col, v)

    override val rows: Set<Row>
        get() = data.keys.map { it.first }.toSet()

    override val columns: Set<Column>
        get() = data.keys.map { it.second }.toSet()
}