package processm.core.log.hierarchical

import processm.core.querylanguage.Type
import java.sql.ResultSet
import java.util.*

internal fun Iterable<Any>.join(transform: (a: Any) -> Any = { it }) = buildString {
    for (item in this@join) {
        append(", ")
        append(transform(item))
    }
}

internal fun <T> Iterator<T>.take(limit: Int): List<T> = ArrayList<T>(limit).also { list ->
    while (list.size < limit && this.hasNext())
        list.add(this.next())
}

internal fun <N : Number> ResultSet.toIdList(): List<N> = ArrayList<N>().also { out ->
    this@toIdList.use {
        while (it.next())
            out.add(it.getObject(1) as N)
    }
}

internal inline fun <reified N : Number> ResultSet.to2DArray(padWith: N): List<Array<N>> =
    ArrayList<Array<N>>().also { out ->
        var maxSize = 0
        this@to2DArray.use {
            while (it.next()) {
                val array = it.getArray(1).array as Array<N>
                if (array.size > maxSize)
                    maxSize = array.size
                out.add(array)
            }
        }
        for (i in out.indices) {
            val old = out[i]
            if (old.size != maxSize)
                out[i] = Array<N>(maxSize) { if (it < old.size) old[it] else padWith }
        }
    }

internal val Type.asAttributeType: String
    get() = when (this) {
        Type.String -> "string"
        Type.Number -> "number"
        Type.Datetime -> "date"
        Type.Boolean -> "boolean"
        else -> throw IllegalArgumentException("Unknown type $this.")
    }

internal val Type.asDBType: String
    get() = when (this) {
        Type.String -> "text"
        Type.Number -> "double precision"
        Type.Datetime -> "timestamptz"
        Type.Boolean -> "boolean"
        else -> throw IllegalArgumentException("Unknown type $this.")
    }