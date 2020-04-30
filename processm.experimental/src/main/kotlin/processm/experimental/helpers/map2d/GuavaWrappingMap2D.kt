package processm.experimental.helpers.map2d

import com.google.common.collect.Table
import processm.core.helpers.map2d.Map2D

/**
 * Implementation wrapping [Table] of Guava, which offers very similar functionality.
 */
class GuavaWrappingMap2D<Row, Column, Value>(val backend: Table<Row, Column, Value>) :
    Map2D<Row, Column, Value> {
    override fun get(row: Row, col: Column): Value? = backend.get(row, col)

    override fun getRow(r: Row): Map2D.View<Column, Value> = object :
        Map2D.View<Column, Value> {

        private val row = backend.row(r)

        override fun get(k: Column): Value? = row[k]

        override fun set(k: Column, v: Value) {
            row[k] = v
        }

        override val keys: Set<Column>
            get() = row.keys
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

    override fun getColumn(c: Column): Map2D.View<Row, Value> = object :
        Map2D.View<Row, Value> {
        private val col = backend.column(c)

        override fun get(k: Row): Value? = col[k]

        override fun set(k: Row, v: Value) {
            col[k] = v
        }

        override val keys: Set<Row>
            get() = col.keys
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
        backend.put(row, col, v)
    }

    override val rows: Set<Row>
        get() = backend.rowKeySet()
    override val columns: Set<Column>
        get() = backend.columnKeySet()
}