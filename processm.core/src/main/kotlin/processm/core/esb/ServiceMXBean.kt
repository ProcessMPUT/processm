package processm.core.esb

import javax.management.MXBean

@MXBean
interface ServiceMXBean {

    /**
     * Status of a service.
     */
    val status: ServiceStatus

    /**
     * Name of a service.
     */
    val name: String

    /**
     * Starts a service. Upon successful termination it should set the status of the service to ServiceStatus.Started.
     */
    fun start()

    /**
     * Stops the service. Upon successful termination it should set the status of the service to ServiceStatus.Stopped.
     */
    fun stop()
}