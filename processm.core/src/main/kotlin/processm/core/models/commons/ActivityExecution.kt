package processm.core.models.commons

/**
 * Represents the possiblity to execute [activity] in some context
 */
interface ActivityExecution {
    /**
     * Activity to execute.
     */
    val activity: Activity

    /**
     * The collection of activities that were the direct cause for executing [activity]. This may be an overestimate
     * if model representation does not allow for exact identification of the cause.
     */
    val cause: Array<out Activity>

    /**
     * Execute [activity] and change the underlying context accordingly.
     * In general, one cannot execute this method more than once for a single object.
     */
    fun execute()
}
