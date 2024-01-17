package processm.core.persistence

import java.sql.ResultSet
import javax.sql.rowset.CachedRowSet
import javax.sql.rowset.RowSetProvider

private val rowSetFactory = RowSetProvider.newFactory()

/**
 * Returns a [CachedRowSet] that caches in memory all rows and metadata of the given [ResultSet]. In contrast to the
 * given [ResultSet], the resulting [CachedRowSet] is disconnected from the database and so the connection corresponding
 * to the given [ResultSet] can be closed.
 * Note that the [CachedRowSet] is a subinterface of [ResultSet].
 */
fun ResultSet.cached(): CachedRowSet = rowSetFactory.createCachedRowSet().also { it.populate(this) }
