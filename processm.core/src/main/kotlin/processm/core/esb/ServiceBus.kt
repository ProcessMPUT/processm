package processm.core.esb

import java.net.URL

/**
 * An interface for managing Enterprise Service Bus.
 */
interface ServiceBus : Service {
    /**
     * URL of the message broker in this ESB.
     */
    val busUrl: URL
}