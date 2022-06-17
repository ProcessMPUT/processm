package processm.core.log

import processm.core.log.hierarchical.QueryResult
import processm.core.log.hierarchical.TranslatedQuery
import processm.core.logging.loggedScope
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.core.querylanguage.Scope

/**
 * A facility for the deletion of logs, traces, and events based on a PQL [query].
 * @property dbName The name of the database to delete from.
 * @property query The PQL query.
 */
class DBXESDeleter(
    val dbName: String,
    val query: Query
) {
    companion object {
        private const val batchSize: Int = Short.MAX_VALUE.toInt()
    }

    init {
        requireNotNull(query.deleteScope) { "The given PQL query must be a delete query." }
    }

    private val pql = TranslatedQuery(dbName, query, batchSize, false)

    /**
     * Deletes XES components from the database.
     */
    operator fun invoke() = loggedScope { logger ->
        DBCache.get(dbName).getConnection().use { conn ->
            conn.autoCommit = false
            val logIds = getIds<Int>(pql.getLogs())
            when (query.deleteScope) {
                Scope.Log -> {
                    logger.trace("Deleting logs with ids ${logIds.joinToString()} from database $dbName.")
                    DBLogCleaner.removeLogs(conn, logIds)
                }
                Scope.Trace -> {
                    val traceIds = logIds.flatMap { getIds<Long>(pql.getTraces(it)) }
                    logger.trace("Deleting traces with ids ${traceIds.joinToString()} from database $dbName.")
                    DBLogCleaner.removeTraces(conn, traceIds)
                }
                Scope.Event -> {
                    val traceIds = logIds.associateWith { getIds<Long>(pql.getTraces(it)) }
                    val eventIds = traceIds.flatMap { (l, t) -> t.flatMap { getIds<Long>(pql.getEvents(l, it)) } }
                    logger.trace("Deleting events with ids ${eventIds.joinToString()} from database $dbName.")
                    DBLogCleaner.removeEvents(conn, eventIds)
                }
                else -> throw IllegalArgumentException("The given PQL query must be a delete query.")
            }
            conn.commit()
        }
    }

    private fun <T : Number> getIds(executor: TranslatedQuery.Executor<out QueryResult>): List<T> {
        val ids = ArrayList<T>()
        executor.use {
            while (executor.hasNext()) {
                ids.addAll(it.nextIds() as List<T>)
            }
        }
        return ids
    }
}
