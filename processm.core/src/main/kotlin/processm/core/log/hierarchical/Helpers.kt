package processm.core.log.hierarchical

import java.sql.ResultSet
import java.util.*

internal fun Iterable<Any>.join(transform: (a: Any) -> Any = { it }) = buildString {
    for (item in this@join) {
        append(", ")
        append(transform(item))
    }
}

internal fun <T> Iterator<T>.take(limit: Int): List<T> {
    val list = ArrayList<T>(limit)
    while (list.size < limit && this.hasNext())
        list.add(this.next())
    return list
}

internal fun <N : Number> ResultSet.toIdList(): List<N> = ArrayList<N>().also { out ->
    this@toIdList.use {
        while (it.next())
            out.add(it.getObject(1) as N)
    }
}

internal fun <N : Number> ResultSet.to2DArray(): List<Array<N>> = ArrayList<Array<N>>().also { out ->
    this@to2DArray.use {
        while (it.next())
            out.add(it.getObject(1) as Array<N>)
    }
}