package processm.core.esb

import jakarta.jms.ExceptionListener
import jakarta.jms.MessageListener

/**
 * The JMS listener for use in [AbstractJobService].
 */
interface JMSListener : MessageListener, ExceptionListener, AutoCloseable {
    /**
     * Starts listening.
     */
    fun listen()
}
