package processm.etl

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.containers.MSSQLServerContainer.MS_SQL_SERVER_PORT
import org.testcontainers.lifecycle.Startables
import processm.dbmodels.models.ConnectionProperties
import processm.dbmodels.models.ConnectionType
import processm.dbmodels.models.DataConnector
import processm.etl.DBMSEnvironment.Companion.TEST_DATABASES_PATH
import processm.logging.logger
import java.sql.Connection
import java.util.*

class MSSQLEnvironment(
    val container: MSSQLServerContainer<*>,
    val dbName: String
) : DBMSEnvironment<MSSQLServerContainer<*>> {

    companion object {

        const val SAKILA_SCHEMA_SCRIPT = "sakila/sql-server-sakila-db/sql-server-sakila-schema.sql"
        const val SAKILA_INSERT_SCRIPT = "sakila/sql-server-sakila-db/sql-server-sakila-insert-data.sql"

        private const val DOCKER_IMAGE = "mcr.microsoft.com/mssql/server:2019-CU28-ubuntu-20.04"
        private val logger = logger()

        fun createContainer(): MSSQLServerContainer<*> = MSSQLServerContainer(DOCKER_IMAGE)
            .withFileSystemBind(TEST_DATABASES_PATH.absolutePath, "/tmp/test-databases/", BindMode.READ_ONLY)
            .withEnv("MSSQL_AGENT_ENABLED", "true")
            .acceptLicense()

        private val sharedContainerDelegate = lazy {
            val container = createContainer()
            Startables.deepStart(listOf(container)).join()
            return@lazy container
        }
        private val sharedContainer by sharedContainerDelegate

        private val sakilaEnv by lazy {
            val env = MSSQLEnvironment(sharedContainer, "sakila")
            env.configureWithScripts(SAKILA_SCHEMA_SCRIPT, SAKILA_INSERT_SCRIPT)
            return@lazy env
        }

        private val WWIEnv by lazy {
            val env = MSSQLEnvironment(
                sharedContainer,
                "WideWorldImporters"
            )
            env.configureWithBackup(
                "WWI/WideWorldImporters-Full.bak",
                "with move 'WWI_Primary' to '/tmp/wwi/WideWorldImporters.mdf', move 'WWI_UserData' to '/tmp/wwi/WideWorldImporters_UserData.ndf', move 'WWI_Log' to '/tmp/wwi/WideWorldImporters.ldf', move 'WWI_InMemory_Data_1' to '/tmp/wwi/WideWorldImporters_InMemory_Data_1'"
            )
            return@lazy env
        }

        fun getSakila() = sakilaEnv

        fun getWWI() = WWIEnv
    }

    fun configureWithScripts(schemaScript: String?, insertScript: String?) {
        fun import(script: String, dbName: String) {
            with(
                container.execInContainer(
                    "/opt/mssql-tools18/bin/sqlcmd",
                    "-U",
                    container.username,
                    "-P",
                    container.password,
                    "-d",
                    dbName,
                    "-i",
                    "/tmp/test-databases/$script",
                    "-No"
                )
            ) {
                logger.debug(stdout)
                logger.warn(stderr)
                check(exitCode == 0)
            }
        }
        if (schemaScript !== null) {
            import(schemaScript, "")
            // At this point schema created the DB and it can be used in the connection URL
            // The name of the param is documented on https://docs.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties?view=sql-server-ver15
            container.withUrlParam("database", dbName)
        }
        if (insertScript !== null)
            import(insertScript, dbName)
    }

    fun configureWithBackup(backupFile: String, restoreCommandSuffix: String) {
        // restore database WideWorldImporters from disk='/tmp/WideWorldImporters-Full.bak' with move 'WWI_Primary' to '/tmp/wwi/WideWorldImporters.mdf', move 'WWI_UserData' to '/tmp/wwi/WideWorldImporters_UserData.ndf', move 'WWI_Log' to '/tmp/wwi/WideWorldImporters.ldf', move 'WWI_InMemory_Data_1' to '/tmp/wwi/WideWorldImporters_InMemory_Data_1';
        container.createConnection("").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { s ->
                s.execute("restore database $dbName from disk='/tmp/test-databases/$backupFile' $restoreCommandSuffix")
            }
            connection.commit()
        }
    }

    override val user: String
        get() = container.username

    override val password: String
        get() = container.password

    override val jdbcUrl: String
        get() = container.withUrlParam("database", dbName).jdbcUrl
    override val connectionProperties: ConnectionProperties
        get() = ConnectionProperties(
            ConnectionType.SqlServer,
            server = container.host,
            port = container.getMappedPort(MS_SQL_SERVER_PORT),
            username = user,
            password = password,
            database = dbName,
            trustServerCertificate = true,
            encrypt = false
        )

    override fun connect(): Connection =
        container.withUrlParam("database", dbName).createConnection("")

    override fun close() {
        if (!sharedContainerDelegate.isInitialized() || container !== sharedContainer)
            container.close() // otherwise it is testcontainer's responsibility to shutdown the container
    }

    override val dataConnector: DataConnector
        get() = DataConnector.new {
            name = UUID.randomUUID().toString()
            connectionProperties =
                ConnectionProperties(ConnectionType.JdbcString, "$jdbcUrl;user=$user;password=$password")
        }
}

