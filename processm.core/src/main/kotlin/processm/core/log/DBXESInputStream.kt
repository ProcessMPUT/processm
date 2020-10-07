package processm.core.log

import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.toFlatSequence
import processm.core.querylanguage.Query
import java.sql.Connection

class DBXESInputStream(dbName: String, query: Query) :
    XESInputStream by DBHierarchicalXESInputStream(dbName, query).toFlatSequence() {
    /**
     * This constructor is provided for backward-compatibility with the previous implementation of XES layer and its
     * use is discouraged in new code.
     * @param logId is the database id of the log. Not to be confused with log:identity:id.
     */
    @Deprecated("Use the primary constructor instead.", level = DeprecationLevel.WARNING)
    constructor(dbName: String, logId: Int) : this(dbName, Query(logId))
}