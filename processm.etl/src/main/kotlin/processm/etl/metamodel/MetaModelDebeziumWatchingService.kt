package processm.etl.metamodel

import jakarta.jms.MapMessage
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.communication.Consumer
import processm.core.esb.Artemis
import processm.core.esb.Service
import processm.core.esb.ServiceStatus
import processm.core.helpers.mapToSet
import processm.core.helpers.toUUID
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.*
import processm.etl.tracker.DebeziumChangeTracker
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.schedule
import kotlin.reflect.KClass

/**
 * Tracks data changes in data sources (defined as [DataConnector]).
 */
class MetaModelDebeziumWatchingService : Service {
    companion object {
        private val logger = logger()
    }
    private val defaultSlotName = "processm"
    private val serverProperty = "server"
    private val portProperty = "port"
    private val usernameProperty = "username"
    private val passwordProperty = "password"
    private val databaseNameProperty = "database"
    private val connectionTypeProperty = "connection-type"
    private val connectionRefreshingPeriod = 30_000L

    private val debeziumTrackers = ConcurrentHashMap<UUID, DebeziumChangeTracker>()
    private val trackerConnectionWatcher = Timer(true)
    private lateinit var consumer: Consumer
    override var status: ServiceStatus = ServiceStatus.Unknown
    override val dependencies: List<KClass<out Service>> = listOf(Artemis::class)

    override fun register() {
        status = ServiceStatus.Stopped
        logger.debug("$name service registered")
        consumer = Consumer()
    }

    override val name: String
        get() = "MetaModel ETL"

    override fun start() {
        initializeDataTrackers()
        consumer.listen(DATA_CONNECTOR_TOPIC, name, ::updateDebeziumConnectionState)
        trackerConnectionWatcher.schedule(connectionRefreshingPeriod, connectionRefreshingPeriod) {
            debeziumTrackers.forEach { (dataConnectorId, tracker) ->
                try {
                    if (!tracker.isAlive) tracker.reconnect()
                } catch (e: Exception) {
                    logger.warn("An issue occurred while trying to reconnect data connector $dataConnectorId", e)
                }
            }
        }
        consumer.start()

        status = ServiceStatus.Started
        logger.info("$name service started")
    }

    override fun stop() {
        try {
            consumer.close()
            trackerConnectionWatcher.cancel()
            trackerConnectionWatcher.purge()
            debeziumTrackers.forEach { (_, tracker) ->
                tracker.close()
            }
        } finally {
            status = ServiceStatus.Stopped
            logger.info("$name service stopped")
        }
    }

    private fun updateDebeziumConnectionState(message: MapMessage): Boolean {
        try {
            val type = message.getString(TYPE)
            val dataStoreId = requireNotNull(message.getString(DATA_STORE_ID)?.toUUID()) { "Missing field: $DATA_STORE_ID." }
            val dataConnectorId =
                requireNotNull(message.getString(DATA_CONNECTOR_ID)?.toUUID()) { "Missing field: $DATA_CONNECTOR_ID." }

            when (type) {
                ACTIVATE -> activate(dataStoreId, dataConnectorId)
                DEACTIVATE -> deactivate(dataStoreId, dataConnectorId)
                RELOAD -> reload(dataStoreId, dataConnectorId)
                else -> throw IllegalArgumentException("Unrecognized type: $type.")
            }
        } catch (e: IllegalArgumentException) {
            logger.warn("A message with incorrect format was received and it will be discarded", e)
        } catch (e: Exception) {
            logger.warn("An error occurred while handling message", e)
            return false
        }

        return true
    }

    private fun initializeDataTrackers() {
        val dataStores = transactionMain {
            return@transactionMain DataStores.selectAll().mapToSet {
                it[DataStores.id].value
            }
        }

        dataStores.forEach { dataStoreId ->
            transaction(DBCache.get("$dataStoreId").database) {
                DataConnectors
                    .selectAll()
                    .forEach dataConnectorsLoop@{ dataConnectorResultRow ->
                        val dataConnectorId = dataConnectorResultRow[DataConnectors.id].value

                        try {
                            val dataConnector = getDataConnector(dataConnectorId)
                            val trackedEntities = getEntitiesToBeTracked(dataConnectorId)

                            if (trackedEntities.isEmpty()) {
                                logger.debug("No connection attempt will be made for the data connector $dataConnectorId due to no entities to be tracked")
                                return@dataConnectorsLoop
                            }

                            val tracker = createDebeziumTracker(dataStoreId, dataConnector, trackedEntities)
                            debeziumTrackers[dataConnectorId] = tracker
                            tracker.start()
                        } catch (e: IllegalArgumentException) {
                            logger.warn("Failed to create a connection for data connector $dataConnectorId due to invalid configuration", e)
                        } catch (e: Exception) {
                            logger.warn("An unknown exception occurred while creating connection for data connector $dataConnectorId", e)
                        }
                    }
            }
        }
    }

    private fun getEntitiesToBeTracked(dataConnectorId: UUID): Set<String>  {
        val sourceClassAlias = Classes.alias("c1")
        val targetClassAlias = Classes.alias("c2")
        return EtlProcessesMetadata
            .innerJoin(AutomaticEtlProcesses)
            .innerJoin(AutomaticEtlProcessRelations)
            .innerJoin(sourceClassAlias, { AutomaticEtlProcessRelations.sourceClassId }, { sourceClassAlias[Classes.id] })
            .innerJoin(targetClassAlias, { AutomaticEtlProcessRelations.targetClassId }, { targetClassAlias[Classes.id] })
            .slice(sourceClassAlias[Classes.name], targetClassAlias[Classes.name])
            .select { EtlProcessesMetadata.dataConnectorId eq dataConnectorId and (EtlProcessesMetadata.isActive) }
            .fold(mutableSetOf()) { entities, relation ->
                entities.add(relation[sourceClassAlias[Classes.name]])
                entities.add(relation[targetClassAlias[Classes.name]])

                return@fold entities
            }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun getDataConnector(dataConnectorId: UUID): DataConnectorDto {
        return requireNotNull(DataConnectors.select { DataConnectors.id eq dataConnectorId }.firstOrNull()?.let {
            try {
                val dataConnector = DataConnector.wrapRow(it)
                val dataConnectorDto = dataConnector.toDto()
                // the below logic misses extracting connection properties from connection string so connectors defined that way are not supported at the moment
                // the limitations is due to Debezium requiring connection configuration in the form of Properties instance
                dataConnectorDto.connectionProperties = Json.decodeFromString<MutableMap<String, String>>(
                    dataConnector.connectionProperties
                )
                return@let dataConnectorDto
            } catch (e: SerializationException) {
                throw IllegalArgumentException("Failed to load connection properties. Connection string based configuration is not yet supported.")
            }
        }) { "No data connector found with the provided ID" }
    }

    private fun activate(dataStoreId: UUID, dataConnectorId: UUID) {
        debeziumTrackers[dataConnectorId]?.close()

        transaction(DBCache.get("$dataStoreId").database) {
            val dataConnector = getDataConnector(dataConnectorId)
            val trackedEntities = getEntitiesToBeTracked(dataConnectorId)

            if (trackedEntities.isEmpty()) {
                logger.debug("No connection attempt will be made for the data connector $dataConnectorId due to no entities to be tracked")
                return@transaction
            }

            val tracker = createDebeziumTracker(dataStoreId, dataConnector, trackedEntities)
            debeziumTrackers[dataConnectorId] = tracker
            tracker.start()
        }
    }

    private fun deactivate(dataStoreId: UUID, dataConnectorId: UUID) {
        debeziumTrackers.remove(dataConnectorId)?.close()
    }

    private fun reload(dataStoreId: UUID, dataConnectorId: UUID) {
        deactivate(dataStoreId, dataConnectorId)
        activate(dataStoreId, dataConnectorId)
    }

    private fun createDebeziumTracker(
        dataStoreId: UUID,
        dataConnector: DataConnectorDto,
        trackedEntities: Set<String>
    ): DebeziumChangeTracker {
        val dataModelId =
            requireNotNull(dataConnector.dataModelId) { "Automatic ETL process has no data model assigned and cannot be tracked" }
        val connectionProperties =
            requireNotNull(dataConnector.connectionProperties) { "Data connector properties are missing" }
        val connectorType = requireNotNull(connectionProperties[connectionTypeProperty]) { "Unknown connection type" }
        val properties = Properties()
            .setDefaults()
            .setConnectorSpecificDefaults(connectorType)
            .setConnectionProperties(dataConnector.id, connectionProperties)
            .setTemporaryFiles(dataConnector.id)
            .setTrackedEntities(trackedEntities)
        val metaModelReader = MetaModelReader(dataModelId)
        return DebeziumChangeTracker(
            properties,
            MetaModel("$dataStoreId", metaModelReader, MetaModelAppender(metaModelReader)),
            dataStoreId,
            dataConnector.id
        )
    }

    private fun Properties.setConnectionProperties(dataConnectorId: UUID, connectionProperties: Map<String, String>): Properties {
        if (connectionProperties.isEmpty()) throw IllegalArgumentException("Unknown connection properties")

        setProperty("database.server.name", "$dataConnectorId")
        setProperty("database.hostname", connectionProperties[serverProperty])
        setProperty("database.port", connectionProperties[portProperty])
        setProperty("database.dbname", connectionProperties[databaseNameProperty])
        setProperty("database.user", connectionProperties[usernameProperty])
        setProperty("database.password", connectionProperties[passwordProperty])
        setProperty("database.sslmode", "disable") //TODO: use user-provided value if available

        return this
    }

    private fun Properties.setConnectorSpecificDefaults(connectorType: String): Properties {
        when (connectorType) {
            "PostgreSql" -> {
                setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
                setProperty("slot.name", defaultSlotName)
                setProperty("slot.drop.on.stop", "false")
                setProperty("plugin.name", "pgoutput")
                setProperty("snapshot.mode", "never")
            }
            "SqlServer" -> {
                setProperty("connector.class", "io.debezium.connector.sqlserver.SqlServerConnector")
                setProperty("snapshot.mode", "schema_only")
            }
            "MySql" -> {
                setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector")
            }
            "OracleDatabase" -> {
                setProperty("connector.class", "io.debezium.connector.oracle.OracleConnector")
            }
            "Db2" -> {
                setProperty("connector.class", "io.debezium.connector.db2.Db2Connector")
            }
        }

        return this
    }

    private fun Properties.setDefaults(): Properties {
        setProperty("max.batch.size", "2048")
        setProperty("event.processing.failure.handling.mode", "warn")
        setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
        setProperty("offset.flush.interval.ms", "60000")
        setProperty("database.history", "io.debezium.relational.history.FileDatabaseHistory")

        return this
    }

    private fun Properties.setTemporaryFiles(dataConnectorId: UUID): Properties {
        setProperty("name", "$dataConnectorId")
        setProperty(
            "offset.storage.file.filename",
            "debezium_offset_${dataConnectorId}.dat"
        )
        setProperty(
            "database.history.file.filename",
            "debezium_dbhistory_${dataConnectorId}.dat"
        )

        return this
    }

    private fun Properties.setTrackedEntities(entities: Set<String>): Properties {
        setProperty("table.include.list", entities.joinToString(",", transform = { "(\\w+\\.)?$it" }))

        return this
    }
}
