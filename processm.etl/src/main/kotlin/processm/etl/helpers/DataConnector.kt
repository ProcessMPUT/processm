package processm.etl.helpers

import com.ibm.db2.jcc.DB2SimpleDataSource
import com.microsoft.sqlserver.jdbc.SQLServerDataSource
import com.mysql.cj.jdbc.MysqlDataSource
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import oracle.jdbc.datasource.impl.OracleDataSource
import org.postgresql.ds.PGSimpleDataSource
import processm.dbmodels.models.ConnectionType
import processm.dbmodels.models.DataConnector
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

fun DataConnector.getConnection(): Connection {
    return if (connectionProperties.startsWith("jdbc")) DriverManager.getConnection(connectionProperties)
    else getDataSource(
        Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), connectionProperties)
    ).connection
}

fun getDataSource(connectionProperties: Map<String, String>): DataSource {
    return when (connectionProperties["connection-type"]?.let(ConnectionType::valueOf) ?: null) {
        ConnectionType.PostgreSql -> PGSimpleDataSource().apply {
            serverNames =
                arrayOf(connectionProperties["server"] ?: throw IllegalArgumentException("Server address is required"))
            portNumbers = intArrayOf(connectionProperties["port"]?.toIntOrNull() ?: 5432)
            user = connectionProperties["username"]
            password = connectionProperties["password"]
            databaseName = connectionProperties["database"]
        }

        ConnectionType.SqlServer -> SQLServerDataSource().apply {
            serverName = connectionProperties["server"] ?: throw IllegalArgumentException("Server address is required")
            portNumber = connectionProperties["port"]?.toIntOrNull() ?: 1433
            user = connectionProperties["username"]
            setPassword(connectionProperties["password"].orEmpty())
            databaseName = connectionProperties["database"]
            // TODO expose this in the interface - see #184
            trustServerCertificate = connectionProperties["trustServerCertificate"]?.toBoolean() ?: false
        }

        ConnectionType.MySql -> MysqlDataSource().apply {
            serverName = connectionProperties["server"] ?: throw IllegalArgumentException("Server address is required")
            portNumber = connectionProperties["port"]?.toIntOrNull() ?: 3306
            user = connectionProperties["username"]
            password = connectionProperties["password"]
            databaseName = connectionProperties["database"]
        }

        ConnectionType.OracleDatabase -> OracleDataSource().apply {
            serverName = connectionProperties["server"] ?: throw IllegalArgumentException("Server address is required")
            portNumber = connectionProperties["port"]?.toIntOrNull() ?: 1521
            user = connectionProperties["username"]
            setPassword(connectionProperties["password"].orEmpty())
            databaseName = connectionProperties["database"]
        }

        ConnectionType.Db2 -> DB2SimpleDataSource().apply {
            serverName = connectionProperties["server"] ?: throw IllegalArgumentException("Server address is required")
            portNumber = connectionProperties["port"]?.toIntOrNull() ?: 50000
            user = connectionProperties["username"]
            setPassword(connectionProperties["password"].orEmpty())
            databaseName = connectionProperties["database"]
        }

        else -> throw Error("Unsupported connection type")
    }
}