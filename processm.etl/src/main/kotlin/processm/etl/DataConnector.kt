package processm.etl

import com.google.gson.Gson
import com.ibm.db2.jcc.DB2SimpleDataSource
import com.microsoft.sqlserver.jdbc.SQLServerDataSource
import com.mysql.cj.jdbc.MysqlDataSource
import oracle.jdbc.datasource.impl.OracleDataSource
import org.postgresql.ds.PGSimpleDataSource
import processm.dbmodels.models.DataConnector
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

/*
FIXME This is a temporary file to be removed once DataConnector.getConnection() is fully developed in the other branch
 */

fun DataConnector.getConnection(): Connection {
    return if (connectionProperties.startsWith("jdbc")) DriverManager.getConnection(connectionProperties)
    else getDataSource(Gson().fromJson<Map<String, String>>(connectionProperties, Map::class.java)).connection
}

private fun getDataSource(connectionProperties: Map<String, String>): DataSource {
    return when (connectionProperties["connection-type"]) {
        "PostgreSql" -> PGSimpleDataSource().apply {
            serverNames =
                arrayOf(connectionProperties["server"] ?: throw IllegalArgumentException("Server address is required"))
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