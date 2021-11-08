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
}
