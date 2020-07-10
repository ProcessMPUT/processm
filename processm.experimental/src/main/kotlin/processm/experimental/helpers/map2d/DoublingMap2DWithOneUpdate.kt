package processm.experimental.helpers.map2d

import processm.core.helpers.map2d.Map2D

class DoublingMap2DWithOneUpdate<Row, Column, Value> :
    Map2D<Row, Column, Value> {

    private data class Reference<Value>(var value: Value)

    private class View<K, V>(private val data: Map<K, Reference<V>>, private val update: (K, V) -> Unit) :
        Map2D.View<K, V> {
        override fun get(k: K): V? = data[k]?.value

        override fun set(k: K, v: V) {
            val ref = data[k]
            if (ref != null)
                ref.value = v
            else
                update(k, v)
        }

        override val keys: Set<K>
            get() = data.keys
        override val entries: Set<Map.Entry<K, V>>
            get() = TODO("Not yet implemented")
        override val size: Int
            get() = TODO("Not yet implemented")
        override val values: Collection<V>
            get() = TODO("Not yet implemented")

        override fun containsKey(key: K): Boolean {
            TODO("Not yet implemented")
        }

        override fun containsValue(value: V): Boolean {
            TODO("Not yet implemented")
        }

        override fun isEmpty(): Boolean {
            TODO("Not yet implemented")
        }
    }

    private val rcv = HashMap<Row, HashMap<Column, Reference<Value>>>()

    private val crv = HashMap<Column, HashMap<Row, Reference<Value>>>()

    override fun get(row: Row, col: Column): Value? = rcv[row]?.get(col)?.value

    override fun getRow(row: Row): Map2D.View<Column, Value> =
        View(rcv[row].orEmpty()) { col, value ->
            set(row, col, value)
        }

    override fun getColumn(col: Column): Map2D.View<Row, Value> =
        View(crv[col].orEmpty()) { row, value ->
            set(row, col, value)
        }

    override fun set(row: Row, col: Column, v: Value) {
        var rowMap = rcv[row]
        var ref =
            rowMap?.get(col) //getOrPut seems to be no better with calling get and then put separately, without keeping any sort of pointer
        if (rowMap == null) {
            rowMap = HashMap()
            rcv[row] = rowMap
        }
        if (ref == null) {
            ref = Reference(v)
            rowMap[col] = ref
            crv.getOrPut(col, { HashMap() })[row] = ref
        } else {
            ref.value = v
        }
    }

    override fun removeColumn(column: Column) {
        TODO("Not yet implemented")
    }

    override fun removeRow(row: Row) {
        TODO("Not yet implemented")
    }

    override val rows: Set<Row>
        get() = rcv.keys
    override val columns: Set<Column>
        get() = crv.keys

}