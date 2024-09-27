package processm.services.logic

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.communication.Producer
import processm.core.persistence.Migrator
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.afterCommit
import processm.dbmodels.etl.jdbc.*
import processm.dbmodels.models.*
import processm.dbmodels.models.ACTIVATE
import processm.dbmodels.models.AutomaticEtlProcess
import processm.dbmodels.models.DEACTIVATE
import processm.dbmodels.models.DataConnector
import processm.dbmodels.models.DataStore
import processm.dbmodels.models.TYPE
import processm.dbmodels.urn
import processm.etl.discovery.SchemaCrawlerExplorer
import processm.etl.helpers.getConnection
import processm.etl.jdbc.notifyUsers
import processm.etl.metamodel.DAGBusinessPerspectiveExplorer
import processm.etl.metamodel.buildMetaModel
import processm.helpers.mapToArray
import processm.helpers.time.toLocalDateTime
import processm.logging.loggedScope
import processm.logging.logger
import processm.services.api.models.*
import processm.services.helpers.ExceptionReason
import processm.services.helpers.defaultPasswordMask
import processm.services.helpers.maskPasswordInJdbcUrl
import java.sql.DriverManager
import java.time.Instant
import java.util.*

typealias ApiDataConnector = processm.services.api.models.DataConnector

class DataStoreService(
    private val accountService: AccountService,
    private val aclService: ACLService,
    private val producer: Producer
) {

    companion object {
        val connectionStringPropertyName = "connection-string"

        private fun DataConnector.toApi(): ApiDataConnector {
            val maskedConnectionProperties = try {
                Json.decodeFromString<MutableMap<String, String>>(connectionProperties).apply {
                    replace("password", defaultPasswordMask)
                }
            } catch (e: SerializationException) {
                val connectionProperties = try {
                    maskPasswordInJdbcUrl(connectionProperties)
                } catch (e: Exception) {
                    logger().warn("Unable to mask connection string in the data connector ${this.id}", e)
                    connectionProperties
                }
                mapOf(connectionStringPropertyName to connectionProperties)
            }
            return ApiDataConnector(
                id.value,
                name,
                lastConnectionStatus,
                lastConnectionStatusTimestamp?.toString(),
                maskedConnectionProperties
            )
        }
    }

    /**
     * Returns all data stores the user identified by [userId] can access
     */
    fun getUserDataStores(userId: UUID): List<DataStore> =
        transactionMain {
            DataStore.wrapRows(
                Groups
                    .innerJoin(UsersInGroups)
                    .crossJoin(DataStores)
                    .join(AccessControlList, JoinType.INNER, AccessControlList.group_id, Groups.id)
                    .slice(DataStores.columns)
                    .select {
                        (UsersInGroups.userId eq userId) and
                                (AccessControlList.urn.column eq concat(
                                    stringLiteral("urn:processm:db/${DataStores.tableName}/"),
                                    DataStores.id
                                )) and
                                (AccessControlList.role_id neq RoleType.None.role.id)
                    }.withDistinct(true)
            ).toList()
        }

    /**
     * Returns the specified data store.
     */
    fun getDataStore(dataStoreId: UUID) = getById(dataStoreId)

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
     * Creates new data store named [name]
     */
    fun createDataStore(userId: UUID, name: String): DataStore {
        return transactionMain {
            val dataStoreId = DataStores.insertAndGetId {
                it[this.name] = name
                it[this.creationDate] = CurrentDateTime
            }
            Migrator.migrate("${dataStoreId.value}")

            val user = accountService.getUser(userId)

            // Add ACL entry for the user being the owner
            aclService.addEntry(dataStoreId.urn, user.privateGroup.id.value, RoleType.Owner)

            return@transactionMain getById(dataStoreId.value)
        }
    }

    /**
     * Removes the data store specified by the [dataStoreId].
     */
    fun removeDataStore(dataStoreId: UUID): Boolean {
        return transactionMain {
            connection.autoCommit = true
            DBCache.get(dataStoreId.toString()).close()
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
    fun getDataConnectors(dataStoreId: UUID): Array<ApiDataConnector> {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            return@transaction DataConnector.wrapRows(DataConnectors.selectAll()).mapToArray {
                it.toApi()
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
                it[this.connectionProperties] = Json.encodeToString(connectionProperties)
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
     * Update name and connection properties of the data connector specified by [dataConnectorId] to, respectively, [newName] and [newConnectionProperties].
     * If any of the new values is `null`, the previous value remains unchanged.
     */
    fun updateDataConnector(
        dataStoreId: UUID,
        dataConnectorId: UUID,
        newName: String?,
        newConnectionProperties: Map<String, String>?,
        newConnectionString: String?
    ) {
        require((newConnectionProperties === null) || (newConnectionString === null)) { "At most one of newConnectionProperties and newConnectionString can be not-null" }
        assertDataStoreExists(dataStoreId)
        transaction(DBCache.get("$dataStoreId").database) {
            DataConnectors.update({ DataConnectors.id eq dataConnectorId }) { stmt ->
                newName?.let { stmt[name] = it }
                newConnectionProperties?.let { stmt[connectionProperties] = Json.encodeToString(it) }
                newConnectionString?.let { stmt[connectionProperties] = it }
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
        getConnection(connectionProperties).close()
    }

    /**
     * Returns case notion suggestions for the data accessed using [dataConnectorId].
     */
    fun getCaseNotionSuggestions(
        dataStoreId: UUID,
        dataConnectorId: UUID
    ): List<CaseNotion> {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            val dataModelId = ensureDataModelExistenceForDataConnector(dataStoreId, dataConnectorId)
            val businessPerspectiveExplorer = DAGBusinessPerspectiveExplorer("$dataStoreId", dataModelId)
            return@transaction businessPerspectiveExplorer.discoverBusinessPerspectives(true)
                .sortedBy { (_, score) -> score }
                .map { (businessPerspective, _) ->
                    val relations = businessPerspective.graph.edgeSet().mapToArray { it.id.value }
                    assert(businessPerspective.convergingClasses.isEmpty()) { "The REST API currently does not support converging classes." }
                    val classes = businessPerspective.identifyingClasses.mapToArray { it.value }
                    CaseNotion(classes, relations)
                }
        }
    }

    /**
     * Returns a graph consisting of all relations discovered in [dataConnectorId].
     */
    fun getRelationshipGraph(
        dataStoreId: UUID,
        dataConnectorId: UUID
    ): RelationshipGraph {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            val dataModelId = ensureDataModelExistenceForDataConnector(dataStoreId, dataConnectorId)
            val businessPerspectiveExplorer = DAGBusinessPerspectiveExplorer("$dataStoreId", dataModelId)
            val classNames = DataModel.findById(dataModelId)!!.classes
            val relationshipGraph = businessPerspectiveExplorer.getRelationshipGraph()
            val relations = relationshipGraph.edgeSet()

            return@transaction RelationshipGraph(
                classNames.mapToArray {
                    RelationshipGraphClassesInner(
                        it.id.value,
                        it.schema?.let { schema -> "$schema.${it.name}" } ?: it.name
                    )
                },
                relations.mapToArray {
                    RelationshipGraphEdgesInner(
                        it.id.value,
                        it.referencingAttributesName.name,
                        it.sourceClass.id.value,
                        it.targetClass.id.value
                    )
                }
            )
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
        relations: List<Int>
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
                    this[AutomaticEtlProcessRelations.automaticEtlProcessId] = etlProcessMetadata.id.value
                    this[AutomaticEtlProcessRelations.relationship] = relation
                }

                commit()
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
            this.isActive = configuration.enabled || nComponents != null
        }
        val cfg = (ETLConfiguration.find { ETLConfigurations.metadata eq etlProcessMetadata.id }.firstOrNull()
            ?: ETLConfiguration.new {}).apply {
            this.metadata = etlProcessMetadata
            this.query = configuration.query
            this.refresh = if (nComponents == null) configuration.refresh?.toLong() else null
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

    private fun SizedIterable<Relationship>.toCaseNotion(): CaseNotion {
        val classes = HashSet<Int>()
        val edges = ArrayList<Int>()
        for (r in this) {
            classes.add(r.sourceClass.id.value)
            classes.add(r.targetClass.id.value)
            edges.add(r.id.value)
        }
        return CaseNotion(classes.toTypedArray(), edges.toTypedArray())
    }

    private fun ETLConfiguration.toJdbcEtlProcessConfiguration(): JdbcEtlProcessConfiguration {
        val cfg = this
        return JdbcEtlProcessConfiguration(
            query = cfg.query,
            enabled = cfg.metadata.isActive,
            batch = cfg.batch,
            traceId = cfg.columnToAttributeMap.first { it.traceId }
                .let { JdbcEtlColumnConfiguration(it.sourceColumn, it.target) },
            eventId = cfg.columnToAttributeMap.first { it.eventId }
                .let { JdbcEtlColumnConfiguration(it.sourceColumn, it.target) },
            attributes = cfg.columnToAttributeMap.filter { !it.eventId && !it.traceId }.mapToArray {
                JdbcEtlColumnConfiguration(it.sourceColumn, it.target)
            },
            refresh = cfg.refresh ?: 0L,
            lastEventExternalId = cfg.lastEventExternalId,
            lastEventExternalIdType = cfg.lastEventExternalIdType
        )
    }

    /**
     * Returns all ETL processes stored in the specified [dataStoreId].
     */
    fun getEtlProcesses(dataStoreId: UUID): List<AbstractEtlProcess> {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            val result = ArrayList<AbstractEtlProcess>()
            AutomaticEtlProcess.all().mapTo(result) {
                AbstractEtlProcess(
                    id = it.id.value,
                    name = it.etlProcessMetadata.name,
                    dataConnectorId = it.etlProcessMetadata.dataConnector.id.value,
                    isActive = it.etlProcessMetadata.isActive,
                    lastExecutionTime = it.etlProcessMetadata.lastExecutionTime?.toLocalDateTime(),
                    type = EtlProcessType.automatic,
                    caseNotion = it.relations.toCaseNotion()
                )
            }
            ETLConfiguration.all().mapTo(result) {
                AbstractEtlProcess(
                    id = it.metadata.id.value,
                    name = it.metadata.name,
                    dataConnectorId = it.metadata.dataConnector.id.value,
                    isActive = it.metadata.isActive,
                    lastExecutionTime = it.metadata.lastExecutionTime?.toLocalDateTime(),
                    type = EtlProcessType.jdbc,
                    configuration = it.toJdbcEtlProcessConfiguration()
                )
            }
            return@transaction result
        }
    }

    /**
     * Changes activation status of the ETL process specified by [etlProcessId].
     */
    fun changeEtlProcessActivationState(dataStoreId: UUID, etlProcessId: UUID, isActive: Boolean) {
        assertDataStoreExists(dataStoreId)
        transaction(DBCache.get("$dataStoreId").database) {
            val etlProcess =
                EtlProcessMetadata.findById(etlProcessId).validateNotNull(ExceptionReason.ETLProcessNotFound)
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
            val etlProcess =
                EtlProcessMetadata.findById(etlProcessId).validateNotNull(ExceptionReason.ETLProcessNotFound)

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

    fun triggerEtlProcess(dataStoreId: UUID, etlProcessId: UUID) {
        assertDataStoreExists(dataStoreId)
        transaction(DBCache.get("$dataStoreId").database) {
            ETLConfiguration
                .find { ETLConfigurations.metadata eq etlProcessId }
                .firstOrNull()
                .validateNotNull(ExceptionReason.ETLProcessNotFound)
                .notifyUsers(TRIGGER)
        }
    }

    /**
     * Asserts that the specified [userId] has at least [role] allowing for access to [dataStoreId]
     */
    fun assertUserHasSufficientPermissionToDataStore(
        userId: UUID,
        dataStoreId: UUID,
        role: RoleType
    ) = aclService.checkAccess(userId, DataStores, dataStoreId, role)

    /**
     * Returns data store struct by its identifier.
     */
    private fun getById(dataStoreId: UUID) = transactionMain {
        return@transactionMain DataStore.findById(dataStoreId).validateNotNull(ExceptionReason.DataStoreNotFound)
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
