package processm.services.logic

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.ibm.db2.jcc.DB2SimpleDataSource
import com.microsoft.sqlserver.jdbc.SQLServerDataSource
import com.mysql.cj.jdbc.MysqlDataSource
import oracle.jdbc.datasource.impl.OracleDataSource
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import org.json.simple.JSONObject
import org.postgresql.ds.PGSimpleDataSource
import processm.core.persistence.Migrator
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import java.sql.DriverManager
import java.util.*
import javax.sql.DataSource

class DataStoreService {
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
    fun getDataConnectors(dataStoreId: UUID): List<DataConnectorDto> =
        transaction(DBCache.get("$dataStoreId").database) {
            val gson = Gson()
            return@transaction DataConnector.wrapRows(DataConnectors.selectAll()).map {
                val connectionProperties =
                    try
                    {
                        gson.fromJson<MutableMap<String, String>>(it.connectionProperties, MutableMap::class.java)
                    }
                    catch (e: JsonSyntaxException) {
                        emptyMap<String, String>().toMutableMap()
                    }
                connectionProperties.replace("password", "********")
                return@map DataConnectorDto(it.id.value, it.name, it.lastConnectionStatus, it.lastConnectionStatusTimestamp, connectionProperties)
            }
        }

    /**
     * Creates a data connector attached to the specified [dataStoreId] using data from [connectionString].
     */
    fun createDataConnector(dataStoreId: UUID, name: String, connectionString: String)
        = transaction(DBCache.get("$dataStoreId").database) {
            val dataConnectorId = DataConnectors.insertAndGetId {
                it[this.name] = name
                it[this.connectionProperties] = connectionString
            }

            return@transaction dataConnectorId.value
        }

    /**
     * Creates a data connector attached to the specified [dataStoreId] using data from [connectionProperties].
     */
    fun createDataConnector(dataStoreId: UUID, name: String, connectionProperties: Map<String, String>)
        = transaction(DBCache.get("$dataStoreId").database) {
            val dataConnectorId = DataConnectors.insertAndGetId {
                it[this.name] = name
                it[this.connectionProperties] = JSONObject(connectionProperties).toString()
            }

            return@transaction dataConnectorId.value
        }

    /**
     * Removes the data connector specified by the [dataConnectorId].
     */
    fun removeDataConnector(dataStoreId: UUID, dataConnectorId: UUID) = transaction(DBCache.get("$dataStoreId").database) {
        return@transaction DataConnectors.deleteWhere {
            DataConnectors.id eq dataConnectorId
        } > 0
    }

    /**
     * Renames the data connector specified by [dataConnectorId] to [newName].
     */
    fun renameDataConnector(dataStoreId: UUID, dataConnectorId: UUID, newName: String) = transaction(DBCache.get("$dataStoreId").database) {
            DataConnectors.update ({ DataConnectors.id eq dataConnectorId }) {
                it[name] = newName
            } > 0
        }

    /**
     * Tests connectivity using the provided [connectionString].
     */
    fun testDatabaseConnection(connectionString: String): Boolean {
        try {
            DriverManager.getConnection(connectionString).use {
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Tests connectivity using the provided [connectionProperties].
     */
    fun testDatabaseConnection(connectionProperties: Map<String, String>): Boolean {
        try {
            getDataSource(connectionProperties).connection.use {
                return true
            }
        } catch (e: Exception) {
            return false
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
     * Asserts that the specified [userId] has the specified [requiredOrganizationRole] allowing for access to [dataStoreId].
     */
    fun assertUserHasSufficientPermissionToDataStore(userId: UUID, dataStoreId: UUID, requiredOrganizationRole: OrganizationRoleDto = OrganizationRoleDto.Reader)
        = transaction(DBCache.getMainDBPool().database) {
            DataStores
                .innerJoin(Organizations)
                .innerJoin(UsersRolesInOrganizations)
                .innerJoin(OrganizationRoles)
                .select { DataStores.id eq dataStoreId and
                        (UsersRolesInOrganizations.userId eq userId) and
                        (UsersRolesInOrganizations.roleId eq OrganizationRoles.getIdByName(requiredOrganizationRole)) }.limit(1).any()
                    || throw ValidationException(ValidationException.Reason.ResourceNotFound, "The specified user account and/or data store does not exist")
    }

    /**
     * Returns data store struct by its identifier.
     */
    private fun getById(dataStoreId: UUID) = transaction(DBCache.getMainDBPool().database) {
        return@transaction DataStore[dataStoreId]
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
}
