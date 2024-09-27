package processm.etl.helpers

import com.ibm.db2.jcc.DB2SimpleDataSource
import com.microsoft.sqlserver.jdbc.SQLServerDataSource
import com.mysql.cj.jdbc.MysqlDataSource
import oracle.jdbc.datasource.impl.OracleDataSource
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.ds.PGSimpleDataSource
import processm.dbmodels.models.ConnectionProperties
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
    if (success && TransactionManager.currentOrNull()?.db == db) {
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
        val connection = getConnection(connectionProperties)
        timestamp(true)
        return connection
    } catch (e: Throwable) {
        timestamp(false)
        throw e
    }
}

fun getConnection(connectionProperties: ConnectionProperties): Connection {
    return when (connectionProperties.connectionType) {
        ConnectionType.JdbcString -> DriverManager.getConnection(connectionProperties.connectionString)

        ConnectionType.CouchDBString -> CouchDBConnection(
            requireNotNull(connectionProperties.connectionString)
                .substring(8)
        )

        ConnectionType.MongoDBString -> MongoDBConnection.fromProcessMUrl(requireNotNull(connectionProperties.connectionString))

        ConnectionType.PostgreSql -> PGSimpleDataSource().apply {
            serverNames =
                arrayOf(connectionProperties.server ?: throw LocalizedException(ExceptionReason.MissingServerName))
            portNumbers = intArrayOf(connectionProperties.port ?: 5432)
            user = connectionProperties.username
            password = connectionProperties.password
            databaseName = connectionProperties.database
        }.connection

        ConnectionType.SqlServer -> SQLServerDataSource().apply {
            serverName = connectionProperties.server ?: throw LocalizedException(ExceptionReason.MissingServerName)
            portNumber = connectionProperties.port ?: 1433
            user = connectionProperties.username
            setPassword(connectionProperties.password.orEmpty())
            databaseName = connectionProperties.database
            trustServerCertificate = connectionProperties.trustServerCertificate ?: false
        }.connection

        ConnectionType.MySql -> MysqlDataSource().apply {
            serverName = connectionProperties.server ?: throw LocalizedException(ExceptionReason.MissingServerName)
            portNumber = connectionProperties.port ?: 3306
            user = connectionProperties.username
            password = connectionProperties.password
            databaseName = connectionProperties.database
        }.connection

        ConnectionType.OracleDatabase -> OracleDataSource().apply {
            serverName = connectionProperties.server ?: throw LocalizedException(ExceptionReason.MissingServerName)
            portNumber = connectionProperties.port ?: 1521
            user = connectionProperties.username
            setPassword(connectionProperties.password.orEmpty())
            databaseName = connectionProperties.database
        }.connection

        ConnectionType.Db2 -> DB2SimpleDataSource().apply {
            serverName = connectionProperties.server ?: throw LocalizedException(ExceptionReason.MissingServerName)
            portNumber = connectionProperties.port ?: 50000
            user = connectionProperties.username
            setPassword(connectionProperties.password.orEmpty())
            databaseName = connectionProperties.database
        }.connection

        ConnectionType.CouchDBProperties -> CouchDBConnection(
            connectionProperties.server ?: throw LocalizedException(ExceptionReason.MissingServerName),
            connectionProperties.port ?: 5984,
            connectionProperties.username,
            connectionProperties.password,
            connectionProperties.database.orEmpty(),
            connectionProperties.https ?: false
        )

        ConnectionType.MongoDBProperties -> MongoDBConnection(
            connectionProperties.server ?: throw LocalizedException(ExceptionReason.MissingServerName),
            connectionProperties.port ?: 27017,
            connectionProperties.username,
            connectionProperties.password,
            connectionProperties.database ?: throw LocalizedException(ExceptionReason.MissingDatabaseName),
            connectionProperties.collection ?: throw LocalizedException(ExceptionReason.MissingCollectionName)
        )

        else -> throw Error("Unsupported connection type")
    }
}