package processm.core.log.hierarchical

import processm.core.querylanguage.Type
import java.sql.ResultSet

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

internal inline fun <N : Number> ResultSet.toIdList(getter: (rs: ResultSet) -> N) = ArrayList<N>().also { out ->
    this@toIdList.use {
        while (it.next())
            out.add(getter(it))
    }
}

internal fun List<Any>.fillPlaceholders(idParam: Any, batchOffset: Long): List<Any> =
    this.map {
        when (it) {
            TranslatedQuery.idPlaceholder -> idParam
            TranslatedQuery.idOffsetPlaceholder -> batchOffset
            else -> it
        }
    }

internal fun ResultSet.to2DIntArray(): List<IntArray> =
    ArrayList<IntArray>().also { out ->
        var maxSize = 0
        this@to2DIntArray.use {
            while (it.next()) {
                val array = ((it.getArray(1) ?: continue).array as Array<Int>).toIntArray()
                if (array.size > maxSize)
                    maxSize = array.size
                out.add(array)
            }
        }
        for (i in out.indices) {
            val old = out[i]
            if (old.size != maxSize)
                out[i] = old.copyOf(maxSize)
        }
    }

internal fun ResultSet.to2DLongArray(): List<LongArray> =
    ArrayList<LongArray>().also { out ->
        var maxSize = 0
        this@to2DLongArray.use {
            while (it.next()) {
                val array = (it.getArray(1).array as Array<Long>).toLongArray()
                if (array.size > maxSize)
                    maxSize = array.size
                out.add(array)
            }
        }
        for (i in out.indices) {
            val old = out[i]
            if (old.size != maxSize)
                out[i] = old.copyOf(maxSize)
        }
    }

internal val Type.asAttributeType: String
    get() = when (this) {
        Type.String -> "string"
        Type.UUID -> "uuid"
        Type.Number -> "float"
        Type.Datetime -> "date"
        Type.Boolean -> "boolean"
        Type.Any -> "any"
        else -> throw IllegalArgumentException("Unknown type $this.")
    }

internal val Type.asDBType: String
    get() = when (this) {
        Type.String -> "text"
        Type.UUID -> "uuid"
        Type.Number -> "double precision"
        Type.Datetime -> "timestamptz"
        Type.Boolean -> "boolean"
        Type.Any -> "text" // fallback to PostgreSQL's TEXT
        else -> throw IllegalArgumentException("Unknown type $this.")
    }
