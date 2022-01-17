package processm.etl.metamodel

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.esb.Service
import processm.core.esb.ServiceStatus
import processm.core.helpers.mapToSet
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import processm.etl.tracker.DebeziumChangeTracker
import java.util.*

/**
 * Manages tracking data changes in data sources (defined as [DataConnector]).
 */
class MetaModelService : Service {
    companion object {
        private val logger = logger()
    }
    private val defaultSlotName = "debezium"
    private val serverProperty = "server"
    private val portProperty = "port"
    private val usernameProperty = "username"
    private val passwordProperty = "password"
    private val databaseNameProperty = "database"
    private val connectionTypeProperty = "connection-type"

    private val dataConnectorsByDataStore = mutableMapOf<UUID, Set<UUID>>()
    private val debeziumTrackers = mutableMapOf<UUID, DebeziumChangeTracker>()
    private val trackedEntities = mutableMapOf<UUID, Set<String>>()
    override fun register() {
        status = ServiceStatus.Stopped
        logger.debug("MetaModel ETL service registered")
    }

    override var status: ServiceStatus = ServiceStatus.Unknown
        private set
    override val name: String
        get() = "MetaModel ETL"

    override fun start() {
        reloadDataConnectors()
//        ensureDataConnectionsAreAlive()

        status = ServiceStatus.Started
        logger.info("MetaModel ETL service started")
    }

    override fun stop() {
        try {
            debeziumTrackers.forEach { (_, tracker) ->
                tracker.close()
            }
        } finally {
            status = ServiceStatus.Stopped
            logger.info("MetaModel ETL service stopped")
        }
    }

    private fun reloadDataConnectors() {
        val dataStores = transaction(DBCache.getMainDBPool().database) {
            return@transaction DataStores.selectAll().mapToSet {
                it[DataStores.id].value
            }
        }

        dataStores.forEach { dataStoreId ->
            transaction(DBCache.get("$dataStoreId").database) {
                val dataConnectorsToDisconnect = mutableSetOf<UUID>()
                val dataConnectorsToConnect = mutableSetOf<UUID>()
                val trackedDataConnectors = dataConnectorsByDataStore[dataStoreId].orEmpty()
                val dataConnectorsToBeTracked = DataConnectors
                    .selectAll()
                    .fold(mutableMapOf<UUID, DataConnectorDto>()) { dataConnectors, dataConnectorResultRow ->
                        val dataConnector =
                            DataConnector.wrapRow(dataConnectorResultRow)
                        try {
                            // the below logic misses extracting connection properties from connection string so connectors defined that way are not supported at the moment
                            // the limitations is caused by the fact that Debezium requires connection configuration in the form of Properties instance
                            val connectionProperties = kotlinx.serialization.json.Json.decodeFromString<MutableMap<String, String>>(
                                dataConnector.connectionProperties
                            )
                            dataConnectors[dataConnector.id.value] = DataConnectorDto(
                                dataConnector.id.value,
                                dataConnector.name,
                                dataConnector.lastConnectionStatus,
                                dataConnector.lastConnectionStatusTimestamp,
                                dataConnector.dataModel?.id?.value,
                                connectionProperties
                            )
                        } catch (e: SerializationException) {
                            logger.warn("Failed to load configuration for data connector ${dataConnector.id.value}. Connection string based configurations are not yet supported.")
                        }

                        return@fold dataConnectors
                    }

                if (trackedDataConnectors != dataConnectorsToBeTracked.keys) {
                    dataConnectorsToDisconnect.addAll(trackedDataConnectors.minus(dataConnectorsToBeTracked.keys))
                    dataConnectorsToConnect.addAll(dataConnectorsToBeTracked.keys.minus(trackedDataConnectors))
                }

                dataConnectorsToBeTracked.keys.forEach it@ { dataConnectorId ->
                    if (dataConnectorsToDisconnect.contains(dataConnectorId)) return@it
                    val sourceClassAlias = Classes.alias("c1")
                    val targetClassAlias = Classes.alias("c2")
                    val entitiesToBeTracked = EtlProcessesMetadata
                        .innerJoin(AutomaticEtlProcesses)
                        .innerJoin(AutomaticEtlProcessRelations)
                        .innerJoin(sourceClassAlias, { AutomaticEtlProcessRelations.sourceClassId }, { sourceClassAlias[Classes.id] })
                        .innerJoin(targetClassAlias, { AutomaticEtlProcessRelations.targetClassId }, { targetClassAlias[Classes.id] })
                        .slice(sourceClassAlias[Classes.name], targetClassAlias[Classes.name])
                        .select { EtlProcessesMetadata.dataConnectorId eq dataConnectorId }
                        .fold(mutableSetOf<String>()) { entities, relation ->
                                    entities.add(relation[sourceClassAlias[Classes.name]])
                                    entities.add(relation[targetClassAlias[Classes.name]])

                                return@fold entities
                            }
                    val trackedDataConnectorEntities = trackedEntities[dataConnectorId].orEmpty()

                    if (entitiesToBeTracked != trackedDataConnectorEntities) {
                        dataConnectorsToDisconnect.add(dataConnectorId)
                        dataConnectorsToConnect.add(dataConnectorId)
                        trackedEntities[dataConnectorId] = entitiesToBeTracked
                    }
                }

                dataConnectorsByDataStore[dataStoreId] = dataConnectorsToBeTracked.keys

                dataConnectorsToDisconnect.forEach { dataConnectorId ->
                    debeziumTrackers.remove(dataConnectorId)?.close()
                }
                dataConnectorsToConnect.forEach it@ { dataConnectorId ->
                    try {
                        val dataConnector = dataConnectorsToBeTracked[dataConnectorId]!!

                        if (dataConnector.dataModelId == null) {
                            throw IllegalArgumentException(
                                "The data connector $dataConnectorId for automatic ETL process has no data model assigned and will not be used")
                        }
                        val connectionProperties = dataConnector.connectionProperties ?: throw IllegalArgumentException(
                            "Unknown connection properties"
                        )
                        val connectorType = dataConnector.connectionProperties?.get(connectionTypeProperty)
                            ?: throw IllegalArgumentException("Unknown connection type")
                        val properties = Properties()
                            .setDefaults()
                            .setConnectorSpecificDefaults(connectorType)
                            .setConnectionProperties(dataConnector.id, connectionProperties)
                            .setTemporaryFiles(dataConnector.id)
                            .setTrackedEntities(trackedEntities[dataConnectorId].orEmpty())

                        val metaModelReader = MetaModelReader(dataConnector.dataModelId!!)
                        val metaModelAppender = MetaModelAppender(metaModelReader)
                        val tracker = DebeziumChangeTracker(
                            properties,
                            MetaModel("$dataStoreId", metaModelReader, metaModelAppender)
                        )

                        tracker.start()
                        debeziumTrackers[dataConnectorId] = tracker
                    } catch (e: IllegalArgumentException) {
                        logger.warn("The data connector $dataConnectorId for automatic ETL process has no data model assigned and will not be used")
                    }
                }
            }
        }
    }

//    private fun ensureDataConnectionsAreAlive() {
//        dataConnectorsByDataStore.forEach { dataStoreId, dataConnectors ->
//            transaction(DBCache.get("$dataStoreId").database) {
//                dataConnectors.forEach {
//                    val dataConnector = dataConnectorsTo
//                }
//            }
//        }
//    }

    private fun Properties.setConnectionProperties(dataConnectorId: UUID, connectionProperties: Map<String, String>): Properties {
        if (connectionProperties.isNullOrEmpty()) throw IllegalArgumentException("Unknown connection properties")

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
//        setProperty("table.include.list", entities.joinToString(",", transform = { "(\\w+\\.)?$it" }))
        setProperty("table.include.list", "public.language")

        return this
    }
}
