package processm.core.log.hierarchical

import java.sql.ResultSet

internal open class QueryResult(
    val entity: ResultSet,
    val attributes: ResultSet,
    val expressions: ResultSet
)

internal class LogQueryResult(
    entity: ResultSet,
    attributes: ResultSet,
    expressions: ResultSet,
    val classifiers: ResultSet? = null,
    val extensions: ResultSet? = null,
    val globals: ResultSet? = null
) : QueryResult(entity, attributes, expressions)