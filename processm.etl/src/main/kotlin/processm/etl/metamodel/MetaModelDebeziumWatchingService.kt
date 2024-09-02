package processm.etl.metamodel

import jakarta.jms.MapMessage
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.communication.Consumer
import processm.core.esb.Artemis
import processm.core.esb.Service
import processm.core.esb.ServiceStatus
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.*
import processm.etl.helpers.getConnection
import processm.etl.tracker.DebeziumChangeTracker
import processm.helpers.*
import processm.logging.loggedScope
import processm.logging.logger
import java.io.File
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
        const val DEBEZIUM_PERSISTENCE_DIRECTORY_PROPERTY = "processm.etl.debezium.persistence.directory"

        private val schemaTableRegex = Regex("^\"(.*)\"\\.\"(.*)\"$")
    }

    private val defaultSlotName = "processm"
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
            val dataStoreId =
                requireNotNull(message.getString(DATA_STORE_ID)?.toUUID()) { "Missing field: $DATA_STORE_ID." }
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
                            val trackedEntities = getEntitiesToBeTracked(dataConnectorId)

                            if (trackedEntities.isEmpty()) {
                                logger.debug("No connection attempt will be made for the data connector $dataConnectorId due to no entities to be tracked")
                                return@dataConnectorsLoop
                            }
                            val dataConnector = DataConnector.wrapRow(dataConnectorResultRow)

                            val tracker = createDebeziumTracker(dataStoreId, dataConnector, trackedEntities)
                            debeziumTrackers[dataConnectorId] = tracker
                            tracker.start()
                        } catch (e: IllegalArgumentException) {
                            logger.warn(
                                "Failed to create a connection for data connector $dataConnectorId due to invalid configuration",
                                e
                            )
                        } catch (e: Exception) {
                            logger.warn(
                                "An unknown exception occurred while creating connection for data connector $dataConnectorId",
                                e
                            )
                        }
                    }
            }
        }
    }

    private fun getEntitiesToBeTracked(dataConnectorId: UUID): Set<Class> {
        val sourceClassAlias = Classes.alias("c1")
        val targetClassAlias = Classes.alias("c2")
        return EtlProcessesMetadata
            .innerJoin(AutomaticEtlProcesses)
            .innerJoin(AutomaticEtlProcessRelations)
            .innerJoin(Relationships)
            .innerJoin(
                sourceClassAlias,
                { Relationships.sourceClassId },
                { sourceClassAlias[Classes.id] })
            .innerJoin(
                targetClassAlias,
                { Relationships.targetClassId },
                { targetClassAlias[Classes.id] })
            .slice(sourceClassAlias.columns + targetClassAlias.columns)
            .select { EtlProcessesMetadata.dataConnectorId eq dataConnectorId and (EtlProcessesMetadata.isActive) }
            .fold(mutableSetOf()) { entities, relation ->
                entities.add(Class.wrapRow(relation, sourceClassAlias))
                entities.add(Class.wrapRow(relation, targetClassAlias))

                return@fold entities
            }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun getDataConnector(dataConnectorId: UUID): DataConnector =
        requireNotNull(DataConnector.findById(dataConnectorId)) { "No data connector found with the provided ID" }

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
        dataConnector: DataConnector,
        trackedEntities: Set<Class>
    ): DebeziumChangeTracker = loggedScope { logger ->
        val dataModelId =
            requireNotNull(dataConnector.dataModel?.id?.value) { "Automatic ETL process has no data model assigned and cannot be tracked" }
        val connectionProperties = try {
            Json.decodeFromString<MutableMap<String, String>>(
                requireNotNull(dataConnector.connectionProperties) { "Data connector properties are missing" }
            )
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Failed to load connection properties. Connection string based configuration is not yet supported.")
        }
        val connectorType =
            ConnectionType.valueOf(requireNotNull(connectionProperties[connectionTypeProperty]) { "Unknown connection type" })
        if (connectorType == ConnectionType.SqlServer) {
            // enable CDC as per https://debezium.io/documentation/reference/stable/connectors/sqlserver.html#_enabling_cdc_on_the_sql_server_database
            dataConnector.getConnection().use { connection ->
                connection.prepareCall("{call sys.sp_cdc_enable_db}").use { call ->
                    call.execute()
                }

                connection.prepareCall("{call sys.sp_cdc_enable_table(?, ?, ?, @role_name =?)}").use { enable ->
                    connection.prepareCall("{call sys.sp_cdc_disable_table(?, ?, ?)}").use { disable ->
                        for (e in trackedEntities) {
                            val schema: String = e.schema ?: "dbo"
                            val name: String = e.name
                            val instance = "ProcessM_${schema}_$name"
                            disable.setString(1, schema)
                            disable.setString(2, name)
                            disable.setString(3, instance)
                            try {
                                disable.execute()
                            } catch (t: Throwable) {
                                logger.warn(
                                    "Disabling CDC for $instance failed. Most probably this error can be ignored",
                                    t
                                )
                            }
                            enable.setString(1, schema)
                            enable.setString(2, name)
                            enable.setString(3, instance)
                            enable.setString(4, connectionProperties["username"])
                            enable.execute()
                        }
                    }
                }
            }
        }
        val properties = Properties()
            .setDefaults()
            .setConnectorSpecificDefaults(connectorType)
            .setConnectionProperties(dataConnector.id.value, connectionProperties)
            .setTemporaryFiles(dataConnector.id.value)
            .setTrackedEntities(trackedEntities)
        return DebeziumChangeTracker(
            properties,
            LogGeneratingDatabaseChangeApplier("$dataStoreId", dataModelId),
            dataStoreId,
            dataConnector.id.value
        )
    }

    private val processmToDebezium = mapOf(
        "server" to "hostname",
        "username" to "user",
        "database" to "dbname"
    )

    private fun Properties.setConnectionProperties(
        dataConnectorId: UUID,
        connectionProperties: Map<String, String>
    ): Properties {
        if (connectionProperties.isEmpty()) throw IllegalArgumentException("Unknown connection properties")

        setProperty("database.server.name", "$dataConnectorId")
        for ((processmKey, value) in connectionProperties) {
            val debeziumKey = processmToDebezium[processmKey] ?: processmKey
            setProperty("database.$debeziumKey", value)
        }
        return this
    }

    private fun Properties.setConnectorSpecificDefaults(connectorType: ConnectionType): Properties {
        when (connectorType) {
            ConnectionType.PostgreSql -> {
                setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
                setProperty("slot.name", defaultSlotName)
                setProperty("slot.drop.on.stop", "false")
                setProperty("plugin.name", "pgoutput")
                setProperty("snapshot.mode", "never")
            }

            ConnectionType.SqlServer -> {
                setProperty("connector.class", "io.debezium.connector.sqlserver.SqlServerConnector")
                setProperty("snapshot.mode", "schema_only")
            }

            ConnectionType.MySql -> {
                setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector")
            }

            ConnectionType.OracleDatabase -> {
                setProperty("connector.class", "io.debezium.connector.oracle.OracleConnector")
            }

            ConnectionType.Db2 -> {
                setProperty("connector.class", "io.debezium.connector.db2.Db2Connector")
            }

            else -> throw LocalizedException(ExceptionReason.UnsupportedDatabaseForAutomaticETL, connectorType.name)
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
        val directory = File(getPropertyIgnoreCase(DEBEZIUM_PERSISTENCE_DIRECTORY_PROPERTY) ?: ".")
        if (!directory.exists())
            directory.mkdirs()
        check(directory.exists() && directory.isDirectory) { "The property `$DEBEZIUM_PERSISTENCE_DIRECTORY_PROPERTY' points to a path that either is not a directory or does not exists and cannot be created, e.g., due to insufficient privileges." }
        setProperty("name", "$dataConnectorId")
        setProperty(
            "offset.storage.file.filename",
            File(directory, "debezium_offset_${dataConnectorId}.dat").absolutePath
        )
        setProperty(
            "database.history.file.filename",
            File(directory, "debezium_dbhistory_${dataConnectorId}.dat").absolutePath
        )

        return this
    }

    private fun Properties.setTrackedEntities(entities: Set<Class>): Properties {
        setProperty("table.include.list", entities.joinToString(",", transform = {
            buildString {
                append("(\\w+\\.)?")
                it.schema?.let { schema ->
                    append(Regex.escape(schema))
                    append("\\.")
                }
                append(Regex.escape(it.name))
            }
        }))

        return this
    }
}
