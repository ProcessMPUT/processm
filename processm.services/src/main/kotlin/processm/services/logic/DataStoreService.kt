package processm.services.logic

import com.ibm.db2.jcc.DB2SimpleDataSource
import com.microsoft.sqlserver.jdbc.SQLServerDataSource
import com.mysql.cj.jdbc.MysqlDataSource
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import oracle.jdbc.datasource.impl.OracleDataSource
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import org.json.simple.JSONObject
import org.postgresql.ds.PGSimpleDataSource
import processm.core.communication.Producer
import processm.core.logging.loggedScope
import processm.core.persistence.Migrator
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import processm.etl.discovery.SchemaCrawlerExplorer
import processm.etl.metamodel.DAGBusinessPerspectiveExplorer
import processm.etl.metamodel.MetaModel
import processm.etl.metamodel.MetaModelReader
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import javax.sql.DataSource

class DataStoreService(private val producer: Producer) {
    /**
     * Returns all data stores for the specified [organizationId].
     */
    fun allByOrganizationId(organizationId: UUID): List<DataStoreDto> {
        return transaction(DBCache.getMainDBPool().database) {
            val query = DataStores.select { DataStores.organizationId eq organizationId }
            return@transaction DataStore.wrapRows(query).map { it.toDto() }
        }
    }

    /**
     * Returns the specified data store.
     */
    fun getDataStore(dataStoreId: UUID) = getById(dataStoreId).toDto()

    fun getDatabaseSize(databaseName: String): Long {
        return transaction(DBCache.getMainDBPool().database) {
            connection.prepareStatement("SELECT pg_database_size(?)", false).run {
                fillParameters(listOf(VarCharColumnType() to databaseName))
                val result = executeQuery()
                return@transaction if (result.next()) result.getLong("pg_database_size") else 0
            }
        }
    }

    /**
     * Creates new data store named [name] and assigned to the specified [organizationId].
     */
    fun createDataStore(organizationId: UUID, name: String): DataStore {
        return transaction(DBCache.getMainDBPool().database) {
            val dataStoreId = DataStores.insertAndGetId {
                it[this.name] = name
                it[this.creationDate] = CurrentDateTime()
                it[this.organizationId] = EntityID(organizationId, Organizations)
            }
            Migrator.migrate("${dataStoreId.value}")
            return@transaction getById(dataStoreId.value)
        }
    }

    /**
     * Removes the data store specified by the [dataStoreId].
     */
    fun removeDataStore(dataStoreId: UUID): Boolean {
        return transaction(DBCache.getMainDBPool().database) {
            connection.autoCommit = true
            SchemaUtils.dropDatabase("\"$dataStoreId\"")
            val dataStoreRemoved = DataStores.deleteWhere {
                DataStores.id eq dataStoreId
            } > 0
            connection.autoCommit = false

            return@transaction dataStoreRemoved
        }
    }

    /**
     * Renames the data store specified by [dataStoreId] to [newName].
     */
    fun renameDataStore(dataStoreId: UUID, newName: String) =
        transaction(DBCache.getMainDBPool().database) {
            DataStores.update ({ DataStores.id eq dataStoreId }) {
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
            DataConnectors.update ({ DataConnectors.id eq dataConnectorId }) {
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
    fun getCaseNotionSuggestions(dataStoreId: UUID, dataConnectorId: UUID): List<Pair<List<Pair<String, String>>, List<Pair<Int, Int>>>> {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            val dataModelId = ensureDataModelExistenceForDataConnector(dataStoreId, dataConnectorId)
            val metaModelReader = MetaModelReader(dataModelId.value)
            val businessPerspectiveExplorer = DAGBusinessPerspectiveExplorer("$dataStoreId", metaModelReader)
            val classNames = metaModelReader.getClassNames()
            return@transaction businessPerspectiveExplorer.discoverBusinessPerspectives(true)
                .sortedBy { (_, score) -> score }
                .map { (businessPerspective, _) ->
                    val relations = businessPerspective.caseNotionClasses
                        .flatMap { classId -> businessPerspective.getSuccessors(classId).map { classId.value to it.value } }
                    businessPerspective.caseNotionClasses.map { classId -> "${classId.value}" to classNames[classId]!! } to relations }
        }
    }

    /**
     * Returns a graph consisting of all relations discovered in [dataConnectorId].
     */
    fun getRelationshipGraph(dataStoreId: UUID, dataConnectorId: UUID): Pair<Map<String, String>, List<Pair<EntityID<Int>, EntityID<Int>>>> {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            val dataModelId = ensureDataModelExistenceForDataConnector(dataStoreId, dataConnectorId)
            val metaModelReader = MetaModelReader(dataModelId.value)
            val businessPerspectiveExplorer = DAGBusinessPerspectiveExplorer("$dataStoreId", metaModelReader)
            val classNames = metaModelReader.getClassNames()
            val relationshipGraph = businessPerspectiveExplorer.getRelationshipGraph()
            val relations = relationshipGraph.edgeSet().map { edgeName -> relationshipGraph.getEdgeSource(edgeName) to relationshipGraph.getEdgeTarget(edgeName) }

            return@transaction classNames.mapKeys { "${it.key}" } to relations
        }
    }

    /**
     * Creates an automatic ETL process in the specified [dataStoreId] using case notion described by [relations].
     */
    fun createAutomaticEtlProcess(dataStoreId: UUID, dataConnectorId: UUID, name: String, relations: List<Pair<String, String>>): UUID {
        assertDataStoreExists(dataStoreId)
        return transaction(DBCache.get("$dataStoreId").database) {
            loggedScope { logger ->
                val etlProcessMetadataId = EtlProcessesMetadata.insertAndGetId {
                    it[this.name] = name
                    it[this.processType] = ProcessTypeDto.Automatic.processTypeName
                    it[this.dataConnectorId] = dataConnectorId
                }
                AutomaticEtlProcesses.insert {
                    it[this.id] = etlProcessMetadataId
                }
                AutomaticEtlProcessRelations.batchInsert(relations) { relation ->
                    try {
                        val sourceClassId = relation.first.toInt()
                        val targetClassId = relation.second.toInt()

                        this[AutomaticEtlProcessRelations.automaticEtlProcessId] = etlProcessMetadataId
                        this[AutomaticEtlProcessRelations.sourceClassId] = sourceClassId
                        this[AutomaticEtlProcessRelations.targetClassId] = targetClassId
                    } catch (e: NumberFormatException) {
                        logger.info("Incorrect data format detected while ")
                    }
                }
                notifyActivated(dataStoreId, dataConnectorId)

                return@transaction etlProcessMetadataId.value
            }
        }
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
            val dataConnectorId = getDataConnectorIdForEtlProcess(etlProcessId)

            (EtlProcessesMetadata
                .update({ EtlProcessesMetadata.id eq etlProcessId }) {
                    it[EtlProcessesMetadata.isActive] = isActive
                } > 0).let { updateStatus ->
                    if (updateStatus && dataConnectorId != null) {
                        if (isActive) notifyActivated(dataStoreId, dataConnectorId)
                        else notifyModified(dataStoreId, dataConnectorId)
                    }
                    return@transaction updateStatus
                }
        }
    }

    /**
     * Removes the ETL process specified by the [etlProcessId].
     */
    fun removeEtlProcess(dataStoreId: UUID, etlProcessId: UUID) {
        assertDataStoreExists(dataStoreId)
        transaction(DBCache.get("$dataStoreId").database) {
            val dataConnectorId = getDataConnectorIdForEtlProcess(etlProcessId)

            (EtlProcessesMetadata.deleteWhere {
                EtlProcessesMetadata.id eq etlProcessId
            } > 0).let { removalStatus ->
                if (removalStatus && dataConnectorId != null) notifyModified(dataStoreId, dataConnectorId)
                return@transaction removalStatus
            }
        }
    }

    /**
     * Asserts that the specified [dataStoreId] is attached to [organizationId].
     */
    fun assertDataStoreBelongsToOrganization(organizationId: UUID, dataStoreId: UUID) = transaction(DBCache.getMainDBPool().database) {
        DataStores.select { DataStores.organizationId eq organizationId and(DataStores.id eq dataStoreId) }.limit(1).any()
                || throw ValidationException(ValidationException.Reason.ResourceNotFound, "The specified organization and/or data store does not exist")
    }

    /**
     * Asserts that the specified [userId] has any of the specified [allowedOrganizationRoles] allowing for access to [dataStoreId].
     */
    fun assertUserHasSufficientPermissionToDataStore(userId: UUID, dataStoreId: UUID, vararg allowedOrganizationRoles: OrganizationRoleDto)
        = transaction(DBCache.getMainDBPool().database) {
            DataStores
                .innerJoin(Organizations)
                .innerJoin(UsersRolesInOrganizations)
                .innerJoin(OrganizationRoles)
                .select { DataStores.id eq dataStoreId and
                        (UsersRolesInOrganizations.userId eq userId) and
                        (UsersRolesInOrganizations.roleId inList allowedOrganizationRoles.map { OrganizationRoles.getIdByName(it) }) }.limit(1).any()
                    || throw ValidationException(ValidationException.Reason.ResourceNotFound, "The specified user account and/or data store does not exist")
    }

    /**
     * Returns data store struct by its identifier.
     */
    private fun getById(dataStoreId: UUID) = transaction(DBCache.getMainDBPool().database) {
        return@transaction DataStore.findById(dataStoreId) ?: throw ValidationException(
            ValidationException.Reason.ResourceNotFound, "The specified data store does not exist or the user has insufficient permissions to it"
        )
    }

    private fun assertDataStoreExists(dataStoreId: UUID) = getById(dataStoreId)

    private fun ensureDataModelExistenceForDataConnector(dataStoreId: UUID, dataConnectorId: UUID): EntityID<Int> {
        val dataConnector = DataConnector.findById(dataConnectorId) ?: throw Error()

        if (dataConnector.dataModel?.id != null) return dataConnector.dataModel!!.id

        dataConnector.getConnection().use { connection ->
            val dataModelId = MetaModel.build("$dataStoreId", metaModelName = "", SchemaCrawlerExplorer(connection))

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

    private fun getDataConnectorIdForEtlProcess(etlProcessId: UUID): UUID? {
        return EtlProcessesMetadata
            .slice(EtlProcessesMetadata.dataConnectorId)
            .select { EtlProcessesMetadata.id eq etlProcessId }
            .firstOrNull()
            ?.get(EtlProcessesMetadata.dataConnectorId)
            ?.value
    }

    private fun getDataSource(connectionProperties: Map<String, String>): DataSource {
        return when (connectionProperties["connection-type"]) {
            "PostgreSql" -> PGSimpleDataSource().apply {
                serverNames = arrayOf(connectionProperties["server"] ?: throw IllegalArgumentException("Server address is required"))
                portNumbers = intArrayOf(connectionProperties["port"]?.toIntOrNull() ?: 5432)
                user = connectionProperties["username"]
                password = connectionProperties["password"]
                databaseName = connectionProperties["database"]
            }
            "SqlServer" -> SQLServerDataSource().apply {
                serverName = connectionProperties["server"] ?: throw IllegalArgumentException("Server address is required")
                portNumber = connectionProperties["port"]?.toIntOrNull() ?: 1433
                user = connectionProperties["username"]
                setPassword(connectionProperties["password"].orEmpty())
                databaseName = connectionProperties["database"]
            }
            "MySql" -> MysqlDataSource().apply {
                serverName = connectionProperties["server"] ?: throw IllegalArgumentException("Server address is required")
                portNumber = connectionProperties["port"]?.toIntOrNull() ?: 3306
                user = connectionProperties["username"]
                password = connectionProperties["password"]
                databaseName = connectionProperties["database"]
            }
            "OracleDatabase" -> OracleDataSource().apply {
                serverName = connectionProperties["server"] ?: throw IllegalArgumentException("Server address is required")
                portNumber = connectionProperties["port"]?.toIntOrNull() ?: 1521
                user = connectionProperties["username"]
                setPassword(connectionProperties["password"].orEmpty())
                databaseName = connectionProperties["database"]
            }
            "Db2" -> DB2SimpleDataSource().apply {
                serverName = connectionProperties["server"] ?: throw IllegalArgumentException("Server address is required")
                portNumber = connectionProperties["port"]?.toIntOrNull() ?: 50000
                user = connectionProperties["username"]
                setPassword(connectionProperties["password"].orEmpty())
                databaseName = connectionProperties["database"]
            }
            else -> throw Error("Unsupported connection type")
        }
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
