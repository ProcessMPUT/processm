package processm.core.esb

/**
 * An interface for micro-service.
 */
interface Service : ServiceMXBean {

    /**
     * Run when ESB registers a service. The service should not start on invoking this function.
     * Upon successful termination it should set the status of the service to ServiceStatus.Stopped.
     */
    fun register()
}