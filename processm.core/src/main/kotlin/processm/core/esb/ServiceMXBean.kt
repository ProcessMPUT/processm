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
     * Starts a service. A relative order of calling start() on different services during startup is undefined and
     * cannot be ensured.
     */
    fun start()

    /**
     * Stops the service. A relative order of calling stop() on different services during finalization is undefined and
     * cannot be ensured.
     */
    fun stop()
}