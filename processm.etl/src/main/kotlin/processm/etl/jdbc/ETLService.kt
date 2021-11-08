package processm.etl.jdbc

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactions.transaction
import org.quartz.*
import org.quartz.SimpleScheduleBuilder.simpleSchedule
import org.quartz.impl.StdSchedulerFactory
import processm.core.esb.Artemis
import processm.core.esb.Service
import processm.core.esb.ServiceStatus
import processm.core.esb.getTopicConnectionFactory
import processm.core.helpers.toUUID
import processm.core.log.AppendingDBXESOutputStream
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.dbmodels.etl.jdbc.*
import processm.dbmodels.models.DataStore
import javax.jms.*
import javax.naming.InitialContext
import kotlin.reflect.KClass

/**
 * A micro-service running the JDBC-based ETL processes. On [start] call it loads the ETL configurations from all
 * datastores and schedules jobs according to these configurations. It listens to the changes reported in the
 * [JDBC_ETL_TOPIC] of Artemis.
 */
class ETLService : Service {
    companion object {
        private const val QUARTZ_CONFIG = "quartz-jdbc.properties"
        private val logger = logger()
        private val jmsContext = InitialContext()
        private val jmsConnFactory = jmsContext.getTopicConnectionFactory()
    }

    private lateinit var scheduler: Scheduler
    private var jmsConnection: TopicConnection? = null
    private var jmsSession: Session? = null
    private var jmsSubscriber: MessageConsumer? = null

    override fun register() {
        scheduler = StdSchedulerFactory(QUARTZ_CONFIG).scheduler
        status = ServiceStatus.Stopped
        logger.debug("JDBC-based ETL service registered")
    }

    override var status: ServiceStatus = ServiceStatus.Unknown
        private set
    override val name: String
        get() = "JDBC-based ETL"
    override val dependencies: List<KClass<out Service>> = listOf(Artemis::class)

    override fun start() {
        scheduler.start()
        loadJobs()
        listenToChanges()
        status = ServiceStatus.Started
        logger.info("JDBC-based ETL service started")
    }

    override fun stop() {
        try {
            jmsSubscriber?.close()
            jmsSession?.close()
            jmsConnection?.close()
            scheduler.shutdown(true)
        } finally {
            jmsSubscriber = null
            jmsSession = null
            jmsConnection = null
            status = ServiceStatus.Stopped
            logger.info("JDBC-based ETL service stopped")
        }
    }

    private fun loadJobs() {
        logger.debug("Loading ETL configurations from datastores")
        val datastores = transaction(DBCache.getMainDBPool().database) {
            DataStore.all().map { it.id.value.toString() }
        }

        for (datastore in datastores) {
            logger.trace("Loading ETL configurations from datastore $datastore")

            transaction(DBCache.get(datastore).database) {
                ETLConfiguration.find {
                    ETLConfigurations.enabled and (ETLConfigurations.refresh.isNotNull() or ETLConfigurations.lastEventExternalId.isNull())
                }.forEach { config ->
                    createJob(datastore, config)
                }
            }
        }
    }

    private fun listenToChanges() {
        jmsConnection = jmsConnFactory.createTopicConnection()
        jmsSession = jmsConnection!!.createTopicSession(false, Session.AUTO_ACKNOWLEDGE)
        val jmsTopic = jmsSession!!.createTopic(JDBC_ETL_TOPIC)
        jmsSubscriber = jmsSession!!.createSharedDurableConsumer(jmsTopic, name)
        jmsSubscriber!!.messageListener = ChangeListener()
        jmsConnection!!.exceptionListener =
            ExceptionListener { logger.error("Error receiving changes to the JDBC-based ETL configurations", it) }
        jmsConnection!!.start()
        logger.debug("Listening to changes in the JDBC-based ETL configurations")
    }

    private fun createJob(datastore: String, config: ETLConfiguration) {
        // TODO: replace ETLConfiguration with ETLConfigurationDto to avoid duplicate database search when job runs
        logger.info("Scheduling the JDBC-based ETL process ${config.name} in the datastore $datastore")
        val job = JobBuilder
            .newJob(ETLJob::class.java)
            .withIdentity(config.id.toString(), datastore)
            .build()
        val trigger = TriggerBuilder
            .newTrigger()
            .withIdentity(config.id.toString(), datastore)
            .startNow()
            .let {
                if (config.refresh !== null) {
                    it.withSchedule(
                        simpleSchedule()
                            .withIntervalInSeconds(config.refresh!!.toInt())
                            .withMisfireHandlingInstructionNowWithRemainingCount()
                            .repeatForever()
                    )
                }
                it
            }
            .build()

        scheduler.scheduleJob(job, setOf(trigger), true)
    }


    @DisallowConcurrentExecution
    class ETLJob : Job {
        override fun execute(context: JobExecutionContext) {
            var config: ETLConfiguration? = null
            val key = context.jobDetail.key
            val id = key.name
            val datastore = key.group
            try {
                transaction(DBCache.get(datastore).database) {
                    config = ETLConfiguration[id.toUUID()!!]
                    logger.info("Running the JDBC-based ETL process ${config!!.name} in datastore $datastore")

                    // DO NOT call output.close(), as it would commit transaction and close connection. Instead, we are
                    // just attaching extra data to the exposed-managed database connection.
                    val output = AppendingDBXESOutputStream((connection as JdbcConnectionImpl).connection)
                    output.write(config!!.toXESInputStream())
                    output.flush()
                }
            } catch (e: Exception) {
                logger.error(e.message, e)
                if (config !== null) {
                    transaction(DBCache.get(datastore).database) {
                        ETLError.new {
                            configuration = config!!.id
                            message = e.message ?: "(not available)"
                            exception = e.stackTraceToString()
                        }
                    }
                }
            } finally {
                logger.info("The JDBC-based ETL process ${config?.name ?: "unknown"} finished")
            }
        }
    }

    private inner class ChangeListener : MessageListener {
        override fun onMessage(message: Message?) {
            require(message is MapMessage) { "Unrecognized message $message." }

            val datastore = message.getString(DATASTORE)
            val type = message.getString(TYPE)
            val id = message.getString(ID)
            when (type) {
                ACTIVATE -> transaction(DBCache.get(datastore).database) {
                    val config = ETLConfiguration[id.toUUID()!!]
                    createJob(datastore, config)
                }
                DEACTIVATE -> {
                    scheduler.deleteJob(JobKey.jobKey(id, datastore))
                }
            }
        }
    }
}
