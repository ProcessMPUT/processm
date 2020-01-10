package processm.core.esb

/**
 * An interface for any piece of code that needs to be started with the application and stopped on exit.
 */
interface Service {
    /**
     * It is called when application starts. A relative order of calling start() on different services is undefined and
     * cannot be ensured.
     */
    fun start()

    /**
     * Stops the service.
     */
    fun stop()
}