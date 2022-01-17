package processm.core.esb

import javax.jms.ExceptionListener
import javax.jms.MessageListener

/**
 * The JMS listener for use in [AbstractJobService].
 */
interface JMSListener : MessageListener, ExceptionListener, AutoCloseable {
    /**
     * Starts listening.
     */
    fun listen()
}
