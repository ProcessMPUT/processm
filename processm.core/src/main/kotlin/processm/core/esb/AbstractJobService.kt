package processm.core.esb

import jakarta.jms.Message
import org.quartz.JobDetail
import org.quartz.Scheduler
import org.quartz.Trigger
import org.quartz.impl.StdSchedulerFactory
import processm.logging.loggedScope
import kotlin.reflect.KClass

/**
 * The base class for services working as consumers of JMS messages and carrying out jobs using a schedule, possibly
 * in parallel.
 *
 * @property schedulerConfig The name of the resource storing the Quartz configuration.
 * @property topic The JMS topic to listen to.
 * @property filter The filter on the JMS topic. See [Message].
 */
abstract class AbstractJobService(
    protected val schedulerConfig: String,
    protected val topic: String,
    protected val filter: String?
) : Service {
    override var status: ServiceStatus = ServiceStatus.Unknown
        protected set
    override val dependencies: List<KClass<out Service>> = listOf(Artemis::class)

    protected lateinit var scheduler: Scheduler
    protected lateinit var jmsListener: JMSListener

    override fun register() = loggedScope { logger ->
        scheduler = StdSchedulerFactory(schedulerConfig).scheduler
        jmsListener = JMSListener()
        status = ServiceStatus.Stopped
        logger.debug("$name service registered.")
    }

    override fun start() = loggedScope { logger ->
        scheduler.start()
        loadJobs().forEach(::schedule)
        jmsListener.listen()
        status = ServiceStatus.Started
        logger.info("$name service started.")
    }

    override fun stop() = loggedScope { logger ->
        jmsListener.close()
        scheduler.shutdown(true)
        status = ServiceStatus.Stopped
        logger.info("$name service stopped.")
    }

    protected fun schedule(pair: Pair<JobDetail, Trigger>) = loggedScope { logger ->
        val (job, trigger) = pair
        logger.debug("Scheduling job ${job.key}...")
        scheduler.scheduleJob(job, setOf(trigger), true)
    }

    protected abstract fun loadJobs(): List<Pair<JobDetail, Trigger>>
    protected abstract fun messageToJobs(message: Message): List<Pair<JobDetail, Trigger>>

    protected inner class JMSListener : AbstractJMSListener(topic, filter, name) {
        override fun onMessage(message: Message?) = loggedScope {
            requireNotNull(message)
            messageToJobs(message).forEach(::schedule)
        }
    }
}
