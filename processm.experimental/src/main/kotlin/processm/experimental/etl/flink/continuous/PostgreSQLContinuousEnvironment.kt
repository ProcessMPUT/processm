package processm.experimental.etl.flink.continuous

import java.io.File

/**
 * The continuous environment for accessing PostgreSQL databases.
 * @see [ContinuousQueryEnvironment]
 */
class PostgreSQLContinuousEnvironment(
    hostname: String = "localhost",
    port: Int = 5432,
    dbName: String,
    user: String,
    password: String,
    stateDirectory: File = getDefaultStateDirectory(hostname, port, user)
) : ContinuousQueryEnvironment(
    hostname,
    port,
    user,
    password,
    PostgresCatalogWithDebeziumConnector("pgcat", hostname, port, dbName, user, password, stateDirectory.absolutePath),
    stateDirectory
)
