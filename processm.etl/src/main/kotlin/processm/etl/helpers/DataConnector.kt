package processm.etl.helpers

import com.ibm.db2.jcc.DB2SimpleDataSource
import com.microsoft.sqlserver.jdbc.SQLServerDataSource
import com.mysql.cj.jdbc.MysqlDataSource
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import oracle.jdbc.datasource.impl.OracleDataSource
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.ds.PGSimpleDataSource
import processm.dbmodels.models.ConnectionType
import processm.dbmodels.models.DataConnector
import processm.etl.jdbc.nosql.CouchDBConnection
import processm.etl.jdbc.nosql.MongoDBConnection
import processm.helpers.ExceptionReason
import processm.helpers.LocalizedException
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime

/**
 * Used by [getConnection] to update [DataConnector.lastConnectionStatus] and [DataConnector.lastConnectionStatusTimestamp]
 *
 * @param success The value to put into [DataConnector.lastConnectionStatus]
 */
private fun DataConnector.timestamp(success: Boolean) {
    if (TransactionManager.currentOrNull()?.db == db) {
        lastConnectionStatusTimestamp = LocalDateTime.now()
        lastConnectionStatus = success
    } else {
        transaction(db) {
            lastConnectionStatusTimestamp = LocalDateTime.now()
            lastConnectionStatus = success
            commit()
        }
    }
}

fun DataConnector.getConnection(): Connection {
    try {
        val connection = if (connectionProperties.startsWith("jdbc")) DriverManager.getConnection(connectionProperties)
        else if (connectionProperties.startsWith("couchdb:")) CouchDBConnection(connectionProperties.substring(8))
        else if (connectionProperties.startsWith("mongodb")) MongoDBConnection.fromProcessMUrl(connectionProperties)
        else getConnection(
            Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), connectionProperties)
        )
        timestamp(true)
        return connection
    } catch (e: Exception) {
        timestamp(false)
        throw e
    }
}

fun getConnection(connectionProperties: Map<String, String>): Connection {
    return when (connectionProperties["connection-type"]?.let(ConnectionType::valueOf)) {
        ConnectionType.PostgreSql -> PGSimpleDataSource().apply {
            serverNames =
                arrayOf(connectionProperties["server"] ?: throw LocalizedException(ExceptionReason.MissingServerName))
            portNumbers = intArrayOf(connectionProperties["port"]?.toIntOrNull() ?: 5432)
            user = connectionProperties["username"]
            password = connectionProperties["password"]
            databaseName = connectionProperties["database"]
        }.connection

        ConnectionType.SqlServer -> SQLServerDataSource().apply {
            serverName = connectionProperties["server"] ?: throw LocalizedException(ExceptionReason.MissingServerName)
            portNumber = connectionProperties["port"]?.toIntOrNull() ?: 1433
            user = connectionProperties["username"]
            setPassword(connectionProperties["password"].orEmpty())
            databaseName = connectionProperties["database"]
            trustServerCertificate = connectionProperties["trustServerCertificate"]?.toBoolean() ?: false
        }.connection

        ConnectionType.MySql -> MysqlDataSource().apply {
            serverName = connectionProperties["server"] ?: throw LocalizedException(ExceptionReason.MissingServerName)
            portNumber = connectionProperties["port"]?.toIntOrNull() ?: 3306
            user = connectionProperties["username"]
            password = connectionProperties["password"]
            databaseName = connectionProperties["database"]
        }.connection

        ConnectionType.OracleDatabase -> OracleDataSource().apply {
            serverName = connectionProperties["server"] ?: throw LocalizedException(ExceptionReason.MissingServerName)
            portNumber = connectionProperties["port"]?.toIntOrNull() ?: 1521
            user = connectionProperties["username"]
            setPassword(connectionProperties["password"].orEmpty())
            databaseName = connectionProperties["database"]
        }.connection

        ConnectionType.Db2 -> DB2SimpleDataSource().apply {
            serverName = connectionProperties["server"] ?: throw LocalizedException(ExceptionReason.MissingServerName)
            portNumber = connectionProperties["port"]?.toIntOrNull() ?: 50000
            user = connectionProperties["username"]
            setPassword(connectionProperties["password"].orEmpty())
            databaseName = connectionProperties["database"]
        }.connection

        ConnectionType.CouchDB -> CouchDBConnection(
            connectionProperties["server"] ?: throw LocalizedException(ExceptionReason.MissingServerName),
            connectionProperties["port"]?.toIntOrNull() ?: 5984,
            connectionProperties["username"],
            connectionProperties["password"],
            connectionProperties["database"].orEmpty(),
            connectionProperties["https"]?.toBoolean() ?: false
        )

        ConnectionType.MongoDB -> MongoDBConnection(
            connectionProperties["server"] ?: throw LocalizedException(ExceptionReason.MissingServerName),
            connectionProperties["port"]?.toIntOrNull() ?: 27017,
            connectionProperties["username"],
            connectionProperties["password"],
            connectionProperties["database"] ?: throw LocalizedException(ExceptionReason.MissingDatabaseName),
            connectionProperties["collection"] ?: throw LocalizedException(ExceptionReason.MissingCollectionName)
        )

        else -> throw Error("Unsupported connection type")
    }
}