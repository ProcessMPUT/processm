package processm.core.esb

import kotlin.reflect.KClass

/**
 * An interface for micro-service.
 */
interface Service : ServiceMXBean {

    /**
     * Run when ESB registers a service. The service should not start on invoking this function.
     * Upon successful termination it should set the status of the service to ServiceStatus.Stopped.
     */
    fun register()

    /**
     * Starts a service. Upon successful termination it should set the status of the service to ServiceStatus.Started.
     */
    fun start()

    /**
     * Stops the service. Upon successful termination it should set the status of the service to ServiceStatus.Stopped.
     */
    fun stop()

    /**
     * The dependent services. These services are guaranteed to run before this service starts and stop after this
     * service stops.
     */
    val dependencies: List<KClass<out Service>>
        get() = emptyList()
}
