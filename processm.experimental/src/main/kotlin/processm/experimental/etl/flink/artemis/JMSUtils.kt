package processm.experimental.etl.flink.artemis

import javax.jms.*
import javax.naming.NameNotFoundException

object JMSUtils {

    fun getDefaultQueueConnectionFactory(context: javax.naming.Context): QueueConnectionFactory {
        val cf = context.lookup("ConnectionFactory")
        return cf as QueueConnectionFactory
    }

    fun getDefaultTopicConnectionFactory(context: javax.naming.Context): TopicConnectionFactory =
        context.lookup("ConnectionFactory") as TopicConnectionFactory


    fun obtainQueue(context: javax.naming.Context, session: QueueSession, name: String): Queue {
        var ignore = true
        while (true) {
            try {
                println("lookup: $name")
                return context.lookup(name) as Queue
            } catch (e: NameNotFoundException) {
                try {
                    println("create: $name")
                    return session.createQueue(name)
                } catch (e: JMSException) {
                    if (!ignore)
                        throw e
                    ignore = false
                }
            }
        }
    }

}
