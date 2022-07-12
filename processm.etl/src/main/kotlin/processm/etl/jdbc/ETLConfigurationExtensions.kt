package processm.etl.jdbc

import jakarta.jms.Session
import jakarta.jms.TopicConnection
import jakarta.jms.TopicPublisher
import jakarta.jms.TopicSession
import org.jetbrains.exposed.sql.name
import processm.core.esb.getTopicConnectionFactory
import processm.dbmodels.etl.jdbc.*
import javax.naming.InitialContext

private val jmsContext = InitialContext()
private val jmsConnFactory = jmsContext.getTopicConnectionFactory()

/**
 * Publishes in the [JDBC_ETL_TOPIC] JMS queue the changes made to this object.
 */
fun ETLConfiguration.notifyUsers() {
    var jmsConnection: TopicConnection? = null
    var jmsSession: TopicSession? = null
    var jmsPublisher: TopicPublisher? = null
    try {
        jmsConnection = jmsConnFactory.createTopicConnection()
        jmsSession = jmsConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
        val jmsTopic = jmsSession.createTopic(JDBC_ETL_TOPIC)
        jmsPublisher = jmsSession.createPublisher(jmsTopic)
        val message = jmsSession.createMapMessage()
        message.setString(DATASTORE, this.db.name)
        message.setString(
            TYPE,
            if (deleted || !enabled || (batch && lastEventExternalId !== null)) DEACTIVATE else ACTIVATE
        )
        message.setString(ID, id.toString())
        jmsPublisher.publish(message)
    } finally {
        jmsPublisher?.close()
        jmsSession?.close()
        jmsConnection?.close()
    }
}
