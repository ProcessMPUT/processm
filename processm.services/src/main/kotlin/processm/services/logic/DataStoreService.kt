package processm.services.logic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import org.json.simple.JSONObject
import processm.core.communication.Producer
import processm.core.helpers.mapToArray
import processm.core.logging.loggedScope
import processm.core.persistence.Migrator
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.afterCommit
import processm.dbmodels.etl.jdbc.*
import processm.dbmodels.models.*
import processm.dbmodels.models.ACTIVATE
import processm.dbmodels.models.DEACTIVATE
import processm.dbmodels.models.TYPE
import processm.etl.discovery.SchemaCrawlerExplorer
import processm.etl.helpers.getDataSource
import processm.etl.jdbc.notifyUsers
import processm.etl.metamodel.DAGBusinessPerspectiveExplorer
import processm.etl.metamodel.MetaModelReader
import processm.etl.metamodel.buildMetaModel
import processm.services.api.models.JdbcEtlColumnConfiguration
import processm.services.api.models.JdbcEtlProcessConfiguration
import processm.services.api.models.OrganizationRole
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.*

class DataStoreService(private val producer: Producer) {
    /**
     * Returns all data stores for the specified [organizationId].
     */
    fun allByOrganizationId(organizationId: UUID): List<DataStoreDto> {
        return transactionMain {
            val query = DataStores.select { DataStores.organizationId eq organizationId }
            return@transactionMain DataStore.wrapRows(query).map { it.toDto() }
        }
    }

    /**
     * Returns the specified data store.
     */
    fun getDataStore(dataStoreId: UUID) = getById(dataStoreId).toDto()

    fun getDatabaseSize(databaseName: String): Long {
        return transactionMain {
            connection.prepareStatement("SELECT pg_database_size(?)", false).run {
                fillParameters(listOf(VarCharColumnType() to databaseName))
                val result = executeQuery()
                return@transactionMain if (result.next()) result.getLong("pg_database_size") else 0
            }
        }
    }

    /**
     * Creates new data store named [name] and assigned to the specified [organizationId].
     */
    fun createDataStore(organizationId: UUID, name: String): DataStore {
        return transactionMain {
            val dataStoreId = DataStores.insertAndGetId {
                it[this.name] = name
                it[this.creationDate] = CurrentDateTime
                it[this.organizationId] = EntityID(organizationId, Organizations)
            }
            Migrator.migrate("${dataStoreId.value}")
            return@transactionMain getById(dataStoreId.value)
        }
    }

    /**
     * Removes the data store specified by the [dataStoreId].
     */
    fun removeDataStore(dataStoreId: UUID): Boolean {
        return transactionMain {
            connection.autoCommit = true
            SchemaUtils.dropDatabase("\"$dataStoreId\"")
            val dataStoreRemoved = DataStores.deleteWhere {
                DataStores.id eq dataStoreId
            } > 0
            connection.autoCommit = false

            return@transactionMain dataStoreRemoved
        }
    }

    /**
     * Renames the data store specified by [dataStoreId] to [newName].
     */
    fun renameDataStore(dataStoreId: UUID, newName: String) =
        transactionMain {
            DataStores.update({ DataStores.id eq dataStoreId }) {
                it[name] = newName
            } > 0
        }

    /**
     * Returns all data connectors for the specified [dataStoreId].
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun getDataConnectors(dataStoreId: UUID): List<DataConnectorDto> {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            return@transaction DataConnector.wrapRows(DataConnectors.selectAll()).map {
                val connectionProperties = try {
                    Json.decodeFromString(it.connectionProperties)
                } catch (e: SerializationException) {
                    emptyMap<String, String>().toMutableMap()
                }
                connectionProperties.replace("password", "********")
                return@map DataConnectorDto(
                    it.id.value,
                    it.name,
                    it.lastConnectionStatus,
                    it.lastConnectionStatusTimestamp,
                    it.dataModel?.id?.value,
                    connectionProperties
                )
            }
        }
    }

    /**
     * Creates a data connector attached to the specified [dataStoreId] using data from [connectionString].
     */
    fun createDataConnector(dataStoreId: UUID, name: String, connectionString: String): UUID {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            val dataConnectorId = DataConnectors.insertAndGetId {
                it[this.name] = name
                it[this.connectionProperties] = connectionString
            }

            return@transaction dataConnectorId.value
        }
    }

    /**
     * Creates a data connector attached to the specified [dataStoreId] using data from [connectionProperties].
     */
    fun createDataConnector(dataStoreId: UUID, name: String, connectionProperties: Map<String, String>): UUID {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            val dataConnectorId = DataConnectors.insertAndGetId {
                it[this.name] = name
                it[this.connectionProperties] = JSONObject(connectionProperties).toString()
            }

            return@transaction dataConnectorId.value
        }
    }

    /**
     * Removes the data connector specified by the [dataConnectorId].
     */
    fun removeDataConnector(dataStoreId: UUID, dataConnectorId: UUID) {
        assertDataStoreExists(dataStoreId)
        transaction(DBCache.get("$dataStoreId").database) {
            (DataConnectors.deleteWhere {
                DataConnectors.id eq dataConnectorId
            } > 0).let { removalStatus ->
                if (removalStatus) notifyDeactivated(dataStoreId, dataConnectorId)
                return@transaction removalStatus
            }
        }
    }

    /**
     * Renames the data connector specified by [dataConnectorId] to [newName].
     */
    fun renameDataConnector(dataStoreId: UUID, dataConnectorId: UUID, newName: String) {
        assertDataStoreExists(dataStoreId)
        transaction(DBCache.get("$dataStoreId").database) {
            DataConnectors.update({ DataConnectors.id eq dataConnectorId }) {
                it[name] = newName
            } > 0
        }
    }

    /**
     * Tests connectivity using the provided [connectionString].
     */
    fun testDatabaseConnection(connectionString: String) {
        DriverManager.getConnection(connectionString).close()
    }

    /**
     * Tests connectivity using the provided [connectionProperties].
     */
    fun testDatabaseConnection(connectionProperties: Map<String, String>) {
        getDataSource(connectionProperties).connection.close()
    }

    /**
     * Returns case notion suggestions for the data accessed using [dataConnectorId].
     */
    fun getCaseNotionSuggestions(
        dataStoreId: UUID,
        dataConnectorId: UUID
    ): List<Pair<List<Pair<String, String>>, List<Pair<Int, Int>>>> {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            val dataModelId = ensureDataModelExistenceForDataConnector(dataStoreId, dataConnectorId)
            val metaModelReader = MetaModelReader(dataModelId.value)
            val businessPerspectiveExplorer = DAGBusinessPerspectiveExplorer("$dataStoreId", metaModelReader)
            val classNames = metaModelReader.getClassNames()
            return@transaction businessPerspectiveExplorer.discoverBusinessPerspectives(true)
                .sortedBy { (_, score) -> score }
                .map { (businessPerspective, _) ->
                    val relations = businessPerspective.identifyingClasses
                        .flatMap { classId ->
                            businessPerspective.getSuccessors(classId).map { classId.value to it.value }
                        }
                    businessPerspective.identifyingClasses.map { classId -> "${classId.value}" to classNames[classId]!! } to relations
                }
        }
    }

    /**
     * Returns a graph consisting of all relations discovered in [dataConnectorId].
     */
    fun getRelationshipGraph(
        dataStoreId: UUID,
        dataConnectorId: UUID
    ): Pair<Map<String, String>, List<Pair<EntityID<Int>, EntityID<Int>>>> {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            val dataModelId = ensureDataModelExistenceForDataConnector(dataStoreId, dataConnectorId)
            val metaModelReader = MetaModelReader(dataModelId.value)
            val businessPerspectiveExplorer = DAGBusinessPerspectiveExplorer("$dataStoreId", metaModelReader)
            val classNames = metaModelReader.getClassNames()
            val relationshipGraph = businessPerspectiveExplorer.getRelationshipGraph()
            val relations = relationshipGraph.edgeSet()
                .map { edgeName -> relationshipGraph.getEdgeSource(edgeName) to relationshipGraph.getEdgeTarget(edgeName) }

            return@transaction classNames.mapKeys { "${it.key}" } to relations
        }
    }

    /**
     * Creates an automatic ETL process in the specified [dataStoreId] using case notion described by [relations].
     */
    fun saveAutomaticEtlProcess(
        etlId: UUID?,
        dataStoreId: UUID,
        dataConnectorId: UUID,
        name: String,
        relations: List<Pair<String, String>>
    ): UUID {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            loggedScope { logger ->
                val etlProcessMetadata =
                    (etlId?.let { EtlProcessMetadata.findById(it) } ?: EtlProcessMetadata.new {}).apply {
                        this.name = name
                        this.processType = ProcessTypeDto.Automatic.processTypeName
                        this.dataConnector = DataConnector[dataConnectorId]
                    }

                AutomaticEtlProcess.findById(etlProcessMetadata.id.value) ?: AutomaticEtlProcesses.insert {
                    it[this.id] = etlProcessMetadata.id.value
                }

                AutomaticEtlProcessRelations.deleteWhere {
                    AutomaticEtlProcessRelations.automaticEtlProcessId eq etlProcessMetadata.id.value
                }

                AutomaticEtlProcessRelations.batchInsert(relations) { relation ->
                    try {
                        val sourceClassId = relation.first.toInt()
                        val targetClassId = relation.second.toInt()

                        this[AutomaticEtlProcessRelations.automaticEtlProcessId] = etlProcessMetadata.id.value
                        this[AutomaticEtlProcessRelations.sourceClassId] = sourceClassId
                        this[AutomaticEtlProcessRelations.targetClassId] = targetClassId
                    } catch (e: NumberFormatException) {
                        logger.info("Incorrect data format detected while ")
                    }
                }
                notifyActivated(dataStoreId, dataConnectorId)

                return@transaction etlProcessMetadata.id.value
            }
        }
    }

    private fun Transaction.saveJdbcEtl(
        etlId: UUID?,
        dataConnectorId: UUID,
        name: String,
        configuration: JdbcEtlProcessConfiguration,
        nComponents: Int? = null
    ): UUID {

        val etlProcessMetadata = (etlId?.let { EtlProcessMetadata.findById(it) }
            ?: EtlProcessMetadata.new {}).apply {
            this.name = name
            this.processType = ProcessTypeDto.JDBC.processTypeName
            this.dataConnector = DataConnector[dataConnectorId]
        }
        val cfg = (ETLConfiguration.find { ETLConfigurations.metadata eq etlProcessMetadata.id }.firstOrNull()
            ?: ETLConfiguration.new {}).apply {
            this.metadata = etlProcessMetadata
            this.query = configuration.query
            this.refresh = if (nComponents == null) configuration.refresh?.toLong() else null
            this.enabled = configuration.enabled || nComponents != null
            this.batch = configuration.batch
            this.lastEventExternalId = if (!configuration.batch) configuration.lastEventExternalId else null
            this.lastEventExternalIdType =
                if (!configuration.batch) configuration.lastEventExternalIdType else null
            this.sampleSize = nComponents
            afterCommit {
                notifyUsers()
            }
        }
        (ETLColumnToAttributeMap.find {
            (ETLColumnToAttributeMaps.configuration eq cfg.id) and (ETLColumnToAttributeMaps.eventId eq true)
        }.firstOrNull()
            ?: ETLColumnToAttributeMap.new { }).apply {
            this.configuration = cfg
            this.sourceColumn = configuration.eventId.source
            this.target = configuration.eventId.target
            this.traceId = false
            this.eventId = true
        }

        (ETLColumnToAttributeMap.find {
            (ETLColumnToAttributeMaps.configuration eq cfg.id) and (ETLColumnToAttributeMaps.traceId eq true)
        }.firstOrNull()
            ?: ETLColumnToAttributeMap.new {}).apply {
            this.configuration = cfg
            this.sourceColumn = configuration.traceId.source
            this.target = configuration.traceId.target
            this.traceId = true
            this.eventId = false
        }

        ETLColumnToAttributeMaps.deleteWhere {
            (ETLColumnToAttributeMaps.configuration eq cfg.id) and
                    (ETLColumnToAttributeMaps.eventId eq false) and
                    (ETLColumnToAttributeMaps.traceId eq false)
        }

        ETLColumnToAttributeMaps.batchInsert(configuration.attributes.asIterable()) { columnCfg ->
            this[ETLColumnToAttributeMaps.configuration] = cfg.id
            this[ETLColumnToAttributeMaps.sourceColumn] = columnCfg.source
            this[ETLColumnToAttributeMaps.target] = columnCfg.target
            this[ETLColumnToAttributeMaps.traceId] = false
            this[ETLColumnToAttributeMaps.eventId] = false
        }
        return etlProcessMetadata.id.value
    }

    /**
     * Creates or updates a JDBC-based ETL process in the specified [dataStoreId]
     */
    fun saveJdbcEtlProcess(
        etlId: UUID?,
        dataStoreId: UUID,
        dataConnectorId: UUID,
        name: String,
        configuration: JdbcEtlProcessConfiguration
    ) = transaction(DBCache.get("$dataStoreId").database) {
        return@transaction saveJdbcEtl(etlId, dataConnectorId, name, configuration)
    }

    /**
     * Reads the configuration of the JDBC ETL process. Returns an API type.
     */
    fun getJdbcEtlProcessConfiguration(
        dataStoreId: UUID,
        etlProcessId: UUID
    ): JdbcEtlProcessConfiguration = transaction(DBCache.get("$dataStoreId").database) {
        val cfg = ETLConfiguration.find {
            ETLConfigurations.metadata eq etlProcessId
        }.first()

        return@transaction JdbcEtlProcessConfiguration(
            query = cfg.query,
            enabled = cfg.enabled,
            batch = cfg.batch,
            traceId = cfg.columnToAttributeMap.first { it.traceId }
                .let { JdbcEtlColumnConfiguration(it.sourceColumn, it.target) },
            eventId = cfg.columnToAttributeMap.first { it.eventId }
                .let { JdbcEtlColumnConfiguration(it.sourceColumn, it.target) },
            attributes = cfg.columnToAttributeMap.filter { !it.eventId && !it.traceId }.mapToArray {
                JdbcEtlColumnConfiguration(it.sourceColumn, it.target)
            },
            refresh = BigDecimal.valueOf(cfg.refresh ?: 0L),
            lastEventExternalId = cfg.lastEventExternalId,
            lastEventExternalIdType = cfg.lastEventExternalIdType
        )
    }

    /**
     * Returns all ETL processes stored in the specified [dataStoreId].
     */
    fun getEtlProcesses(dataStoreId: UUID): List<EtlProcessMetadataDto> {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            return@transaction EtlProcessMetadata.wrapRows(EtlProcessesMetadata.selectAll()).map { it.toDto() }
        }
    }

    /**
     * Changes activation status of the ETL process specified by [etlProcessId].
     */
    fun changeEtlProcessActivationState(dataStoreId: UUID, etlProcessId: UUID, isActive: Boolean) {
        assertDataStoreExists(dataStoreId)
        transaction(DBCache.get("$dataStoreId").database) {
            val etlProcess = EtlProcessMetadata.findById(etlProcessId).validateNotNull(Reason.ResourceNotFound)
            etlProcess.isActive = isActive

            when (etlProcess.processType) {
                ProcessTypeDto.Automatic.processTypeName ->
                    if (isActive) notifyActivated(dataStoreId, etlProcess.dataConnector.id.value)
                    else notifyModified(dataStoreId, etlProcess.dataConnector.id.value)

                ProcessTypeDto.JDBC.processTypeName ->
                    ETLConfiguration
                        .find { ETLConfigurations.metadata eq etlProcess.id }
                        .first()
                        .apply {
                            afterCommit {
                                this as ETLConfiguration
                                notifyUsers()
                            }
                        }

                else -> throw IllegalArgumentException("Unknown ETL process type: ${etlProcess.processType}.")
            }
        }
    }

    data class EtlProcessInfo(val logIdentityId: UUID, val errors: List<ETLErrorDto>, val lastExecutionTime: Instant?)

    fun getEtlProcessInfo(dataStoreId: UUID, etlProcessId: UUID) =
        transaction(DBCache.get("$dataStoreId").database) {
            val etlProcessMetadata = EtlProcessMetadata.findById(etlProcessId)
                ?: throw NoSuchElementException("Cannot find ETL process with metadata ID `$etlProcessId'")
            val logIdentityId = when (ProcessTypeDto.byNameInDatabase(etlProcessMetadata.processType)) {
                ProcessTypeDto.JDBC -> ETLConfiguration.find { ETLConfigurations.metadata eq etlProcessId }
                    .first().logIdentityId

                ProcessTypeDto.Automatic -> etlProcessId
            }
            return@transaction EtlProcessInfo(
                logIdentityId,
                etlProcessMetadata.errors.map(ETLError::toDto),
                etlProcessMetadata.lastExecutionTime
            )
        }

    /**
     * Removes the ETL process specified by the [etlProcessId].
     */
    fun removeEtlProcess(dataStoreId: UUID, etlProcessId: UUID) {
        assertDataStoreExists(dataStoreId)
        transaction(DBCache.get("$dataStoreId").database) {
            val etlProcess = EtlProcessMetadata.findById(etlProcessId).validateNotNull(Reason.ResourceNotFound)

            when (etlProcess.processType) {
                ProcessTypeDto.Automatic.processTypeName -> {
                    etlProcess.delete()
                    notifyModified(dataStoreId, etlProcess.dataConnector.id.value)
                }

                ProcessTypeDto.JDBC.processTypeName -> {
                    ETLConfiguration
                        .find { ETLConfigurations.metadata eq etlProcess.id }
                        .first()
                        .apply {
                            delete() // FIXME: do we really need delete() here? ETLConfiguration has foreign key constraint with ON DELETE CASCADE, so Exposed should be informed that ETLConfiguration is deleted
                            afterCommit {
                                this as ETLConfiguration
                                notifyUsers()
                            }
                        }
                    etlProcess.delete()
                }

                else -> throw IllegalArgumentException("Unknown ETL process type: ${etlProcess.processType}.")
            }
        }
    }

    /**
     * Asserts that the specified [dataStoreId] is attached to [organizationId].
     */
    fun assertDataStoreBelongsToOrganization(organizationId: UUID, dataStoreId: UUID) = transactionMain {
        DataStores.select { DataStores.organizationId eq organizationId and (DataStores.id eq dataStoreId) }.limit(1)
            .any()
                || throw ValidationException(
            Reason.ResourceNotFound,
            "The specified organization and/or data store does not exist"
        )
    }

    /**
     * Asserts that the specified [userId] has any of the specified [allowedOrganizationRoles] allowing for access to [dataStoreId].
     */
    fun assertUserHasSufficientPermissionToDataStore(
        userId: UUID,
        dataStoreId: UUID,
        vararg allowedOrganizationRoles: OrganizationRole
    ) = transactionMain {
        DataStores
            .innerJoin(Organizations)
            .innerJoin(UsersRolesInOrganizations)
            .innerJoin(Roles)
            .select {
                DataStores.id eq dataStoreId and
                        (UsersRolesInOrganizations.userId eq userId) and
                        (Roles.name inList allowedOrganizationRoles.map { it.value })
            }.limit(1).any()
                || throw ValidationException(
            Reason.ResourceNotFound,
            "The specified user account and/or data store does not exist"
        )
    }

    /**
     * Returns data store struct by its identifier.
     */
    private fun getById(dataStoreId: UUID) = transactionMain {
        return@transactionMain DataStore.findById(dataStoreId) ?: throw ValidationException(
            Reason.ResourceNotFound,
            "The specified data store does not exist or the user has insufficient permissions to it"
        )
    }

    private fun assertDataStoreExists(dataStoreId: UUID) = getById(dataStoreId)

    private fun ensureDataModelExistenceForDataConnector(dataStoreId: UUID, dataConnectorId: UUID): EntityID<Int> {
        val dataConnector = DataConnector.findById(dataConnectorId) ?: throw Error()

        if (dataConnector.dataModel?.id != null) return dataConnector.dataModel!!.id

        dataConnector.getConnection().use { connection ->
            val dataModelId = buildMetaModel("$dataStoreId", metaModelName = "", SchemaCrawlerExplorer(connection))

            DataConnectors.update({ DataConnectors.id eq dataConnectorId }) {
                it[DataConnectors.dataModelId] = dataModelId
            }

            return dataModelId
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun DataConnector.getConnection(): Connection {
        return if (connectionProperties.startsWith("jdbc")) DriverManager.getConnection(connectionProperties)
        else getDataSource(Json.decodeFromString(connectionProperties)).connection
    }

    fun createSamplingJdbcEtlProcess(
        dataStoreId: UUID,
        dataConnectorId: UUID,
        name: String,
        configuration: JdbcEtlProcessConfiguration,
        nComponents: Int
    ) = transaction(DBCache.get("$dataStoreId").database) {
        return@transaction saveJdbcEtl(null, dataConnectorId, name, configuration, nComponents)
    }

    fun notifyActivated(dataStoreId: UUID, dataConnectorId: UUID) {
        notifyStateChanged(dataStoreId, dataConnectorId, ACTIVATE)
    }

    fun notifyDeactivated(dataStoreId: UUID, dataConnectorId: UUID) {
        notifyStateChanged(dataStoreId, dataConnectorId, DEACTIVATE)
    }

    fun notifyModified(dataStoreId: UUID, dataConnectorId: UUID) {
        notifyStateChanged(dataStoreId, dataConnectorId, RELOAD)
    }

    private fun notifyStateChanged(dataStoreId: UUID, dataConnectorId: UUID, type: String) {
        producer.produce(DATA_CONNECTOR_TOPIC) {
            setString(TYPE, type)
            setString(DATA_STORE_ID, "$dataStoreId")
            setString(DATA_CONNECTOR_ID, "$dataConnectorId")
        }
    }
}
