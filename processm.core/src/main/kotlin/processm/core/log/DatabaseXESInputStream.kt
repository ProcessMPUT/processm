package processm.core.log

import processm.core.log.hierarchical.DatabaseHierarchicalXESInputStream
import processm.core.log.hierarchical.toFlatSequence
import processm.core.querylanguage.Query

class DatabaseXESInputStream(query: Query) :
    XESInputStream by DatabaseHierarchicalXESInputStream(query).toFlatSequence() {
    /**
     * This constructor is provided for backward-compatibility with the previous implementation of XES layer and its
     * use is discouraged in new code.
     * @param logId is the database id of the log. Not to be confused with log:identity:id.
     */
    @Deprecated("Use the primary constructor instead.", level = DeprecationLevel.WARNING)
    constructor(logId: Int) : this(Query(logId))
}
