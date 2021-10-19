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
        transaction(DBCache.getMainDBPool().database) {
            val query = DataConnectors.select { DataConnectors.dataStoreId eq dataStoreId }
            val gson = Gson()
            return@transaction DataConnector.wrapRows(query).map {
                val connectionProperties =
                    try
                    {
                        gson.fromJson<MutableMap<String, String>>(it.connectionProperties, MutableMap::class.java)
                    }
                    catch (e: JsonSyntaxException) {
                        emptyMap<String, String>().toMutableMap()
                    }
                connectionProperties.replace("password", "********")
                return@map DataConnectorDto(it.id.value, it.name, it.lastConnectionStatus, connectionProperties)
            }
        }

    fun createDataConnector(dataStoreId: UUID, name: String, connectionString: String)
        = transaction(DBCache.getMainDBPool().database) {
            val dataConnectorId = DataConnectors.insertAndGetId {
                it[this.name] = name
                it[this.dataStoreId] = EntityID(dataStoreId, DataStores)
                it[this.connectionProperties] = connectionString
            }

            return@transaction dataConnectorId.value
        }

    fun createDataConnector(dataStoreId: UUID, name: String, connectionProperties: Map<String, String>)
        = transaction(DBCache.getMainDBPool().database) {
            val dataConnectorId = DataConnectors.insertAndGetId {
                it[this.name] = name
                it[this.dataStoreId] = EntityID(dataStoreId, DataStores)
                it[this.connectionProperties] = JSONObject(connectionProperties).toString()
            }

            return@transaction dataConnectorId.value
        }

    fun removeDataConnector(dataConnectorId: UUID) = transaction(DBCache.getMainDBPool().database) {
        return@transaction DataConnectors.deleteWhere {
            DataConnectors.id eq dataConnectorId
        } > 0
    }

    fun renameDataConnector(dataConnectorId: UUID, newName: String) = transaction(DBCache.getMainDBPool().database) {
            DataConnectors.update ({ DataConnectors.id eq dataConnectorId }) {
                it[name] = newName
            } > 0
        }

    fun testDatabaseConnection(connectionString: String): Boolean {
        try {
            DriverManager.getConnection(connectionString).use {
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }

    fun testDatabaseConnection(connectionProperties: Map<String, String>): Boolean {
        try {
            getDataSource(connectionProperties).connection.use {
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }

    fun ensureDataStoreBelongsToOrganization(organizationId: UUID, dataStoreId: UUID) = transaction(DBCache.getMainDBPool().database) {
        DataStores.select { DataStores.organizationId eq organizationId }.limit(1).any()
                || throw ValidationException(ValidationException.Reason.ResourceNotFound, "The specified organization and/or data store does not exist")
    }

    fun ensureUserHasSufficientPermissionToDataStore(userId: UUID, dataStoreId: UUID, requiredOrganizationRole: OrganizationRoleDto = OrganizationRoleDto.Reader)
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
