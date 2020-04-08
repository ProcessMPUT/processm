package processm.core.models.commons

/**
 * Represents the possiblity to execute [activity] in some context
 */
interface AbstractActivityExecution {
    val activity: AbstractActivity

    /**
     * Execute [activity] and change the underlying context accordingly.
     * In general, one cannot execute this method more than once for a single object.
     */
    fun execute()
}