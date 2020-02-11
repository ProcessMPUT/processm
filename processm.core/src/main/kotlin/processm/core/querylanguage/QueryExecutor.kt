package processm.core.querylanguage

/**
 * Executes the given query on the log.
 */
interface QueryExecutor<T> {

    /**
     * Executes the given query on the log returning the object representing the output of processing.
     * @param query The query to execute.
     */
    fun execute(query: Query): T
}
