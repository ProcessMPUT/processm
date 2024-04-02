package processm.etl.jdbc

import jakarta.jms.MapMessage
import jakarta.jms.Message
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactions.transaction
import org.quartz.*
import org.quartz.SimpleScheduleBuilder.simpleSchedule
import processm.core.esb.AbstractJobService
import processm.core.esb.ServiceJob
import processm.core.log.AppendingDBXESOutputStream
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.etl.jdbc.*
import processm.dbmodels.models.DataStores
import processm.etl.helpers.nextVersion
import processm.etl.helpers.reportETLError
import processm.helpers.toUUID
import processm.logging.loggedScope

/**
 * A micro-service running the JDBC-based ETL processes. On [start] call it loads the ETL configurations from all
 * datastores and schedules jobs according to these configurations. It listens to the changes reported in the
 * [JDBC_ETL_TOPIC] of Artemis.
 */
class ETLService : AbstractJobService(QUARTZ_CONFIG, JDBC_ETL_TOPIC, null) {
    companion object {
        private const val QUARTZ_CONFIG = "quartz-jdbc.properties"
    }

    override val name: String
        get() = "JDBC-based ETL"

    override fun loadJobs(): List<Pair<JobDetail, Trigger>> = loggedScope { logger ->
        logger.debug("Loading ETL configurations from datastores...")
        val datastores = transactionMain {
            DataStores.slice(DataStores.id).selectAll().map { it[DataStores.id].value.toString() }
        }

        return datastores.flatMap { datastore ->
            logger.trace("Loading ETL configurations from datastore $datastore...")

            transaction(DBCache.get(datastore).database) {
                ETLConfiguration.find {
                    ETLConfigurations.enabled and (ETLConfigurations.refresh.isNotNull() or ETLConfigurations.lastEventExternalId.isNull())
                }.map { config ->
                    createJob(datastore, config)
                }
            }
        }
    }

    override fun messageToJobs(message: Message): List<Pair<JobDetail, Trigger>> {
        require(message is MapMessage) { "Unrecognized message $message." }

        val datastore = message.getString(DATASTORE)
        val type = message.getString(TYPE)
        val id = message.getString(ID)
        return when (type) {
            ACTIVATE -> transaction(DBCache.get(datastore).database) {
                val config = ETLConfiguration[id.toUUID()!!]
                listOf(createJob(datastore, config))
            }

            DEACTIVATE -> {
                scheduler.deleteJob(JobKey.jobKey(id, datastore))
                emptyList()
            }

            TRIGGER -> loggedScope { logger ->
                val key = JobKey.jobKey(id, datastore)
                if (scheduler.checkExists(key)) {
                    logger.debug("Triggering an existing job {}", key)
                    scheduler.triggerJob(key)
                    emptyList()
                } else {
                    logger.debug("Triggering a non-existing job {}", key)
                    val job = JobBuilder
                        .newJob(ETLJob::class.java)
                        .withIdentity(id, datastore)
                        .build()
                    val trigger = TriggerBuilder
                        .newTrigger()
                        .withIdentity(id, datastore)
                        .startNow()
                        .build()
                    listOf(job to trigger)
                }
            }

            else -> throw IllegalArgumentException("Unrecognized type: $type.")
        }
    }

    private fun createJob(datastore: String, config: ETLConfiguration): Pair<JobDetail, Trigger> = loggedScope {
        // TODO: replace ETLConfiguration with ETLConfigurationDto to avoid duplicate database search when job runs
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

        return job to trigger
    }

    class ETLJob : ServiceJob {
        override fun execute(context: JobExecutionContext) = loggedScope { logger ->
            var config: ETLConfiguration? = null
            val key = context.jobDetail.key
            val id = key.name
            val datastore = key.group
            var name: String = "unknown"
            try {
                transaction(DBCache.get(datastore).database) {
                    config = ETLConfiguration[id.toUUID()!!]
                    name = config?.metadata?.name ?: name
                    logger.info("Running the JDBC-based ETL process ${config!!.metadata.name} in datastore $datastore")

                    val sqlConnection = (connection as JdbcConnectionImpl).connection
                    // DO NOT call output.close(), as it would commit transaction and close connection. Instead, we are
                    // just attaching extra data to the exposed-managed database connection.
                    val output = AppendingDBXESOutputStream(sqlConnection, version = sqlConnection.nextVersion())
                    output.write(
                        config!!.toXESInputStream()
                            .let { stream -> config!!.sampleSize?.let { stream.take(it) } ?: stream })
                    output.flush()
                }
            } catch (e: Exception) {
                logger.error(e.message, e)
                if (config !== null) {
                    transaction(DBCache.get(datastore).database) {
                        reportETLError(config!!.metadata.id, e)
                    }
                }
            } finally {
                logger.info("The JDBC-based ETL process $name finished")
            }
        }
    }
}
