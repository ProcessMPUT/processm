package processm.etl

import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.MountableFile
import processm.core.logging.logger
import processm.dbmodels.models.DataConnector
import java.sql.Connection
import java.util.*

class MSSQLEnvironment(
    val container: MSSQLServerContainer<*>,
    val dbName: String
) : DBMSEnvironment<MSSQLServerContainer<*>> {

    companion object {
        private const val DOCKER_IMAGE = "mcr.microsoft.com/mssql/server:2019-CU12-ubuntu-20.04"
        private val logger = logger()

        fun createContainer(): MSSQLServerContainer<*> = MSSQLServerContainer<MSSQLServerContainer<*>>(DOCKER_IMAGE)
            .acceptLicense()

        private val sharedContainerDelegate = lazy {
            val container = createContainer()
            Startables.deepStart(listOf(container)).join()
            return@lazy container
        }
        private val sharedContainer by sharedContainerDelegate

        private val sakilaEnv by lazy {
            val env = MSSQLEnvironment(sharedContainer, "sakila")
            env.configureWithScripts(
                "sakila/sql-server-sakila-db/sql-server-sakila-schema.sql",
                "sakila/sql-server-sakila-db/sql-server-sakila-insert-data.sql"
            )
            return@lazy env
        }

        private val WWIEnv by lazy {
            val env = MSSQLEnvironment(
                sharedContainer,
                "WideWorldImporters"
            )
            env.configureWithBackup(
                "mssql/WWI/WideWorldImporters-Full.bak",
                "with move 'WWI_Primary' to '/tmp/wwi/WideWorldImporters.mdf', move 'WWI_UserData' to '/tmp/wwi/WideWorldImporters_UserData.ndf', move 'WWI_Log' to '/tmp/wwi/WideWorldImporters.ldf', move 'WWI_InMemory_Data_1' to '/tmp/wwi/WideWorldImporters_InMemory_Data_1'"
            )
            return@lazy env
        }

        fun getSakila() = sakilaEnv

        fun getWWI() = WWIEnv
    }

    fun configureWithScripts(schemaScript: String, insertScript: String) {
        val containerSchemaScript = "/tmp/schema.sql"
        container.copyFileToContainer(MountableFile.forClasspathResource(schemaScript), containerSchemaScript)

        with(
            container.execInContainer(
                "/opt/mssql-tools/bin/sqlcmd",
                "-U",
                container.username,
                "-P",
                container.password,
                "-i",
                containerSchemaScript
            )
        ) {
            logger.debug(stdout)
            logger.warn(stderr)
            check(exitCode == 0)
        }

        // At this point schema created the DB and it can be used in the connection URL
        // The name of the param is documented on https://docs.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties?view=sql-server-ver15
        container.withUrlParam("database", dbName)

        container.createConnection("").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { s ->
                s.execute(this::class.java.classLoader.getResource(insertScript)!!.readText())
            }
            connection.commit()
        }
    }

    fun configureWithBackup(backupFile: String, restoreCommandSuffix: String) {
        val backupFileInContainer = "/tmp/db.bak"
        container.copyFileToContainer(MountableFile.forClasspathResource(backupFile), backupFileInContainer)
        // restore database WideWorldImporters from disk='/tmp/WideWorldImporters-Full.bak' with move 'WWI_Primary' to '/tmp/wwi/WideWorldImporters.mdf', move 'WWI_UserData' to '/tmp/wwi/WideWorldImporters_UserData.ndf', move 'WWI_Log' to '/tmp/wwi/WideWorldImporters.ldf', move 'WWI_InMemory_Data_1' to '/tmp/wwi/WideWorldImporters_InMemory_Data_1';
        container.createConnection("").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { s ->
                s.execute("restore database $dbName from disk='${backupFileInContainer}' $restoreCommandSuffix")
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

    override fun connect(): Connection =
        container.withUrlParam("database", dbName).createConnection("")

    override fun close() {
        if (!sharedContainerDelegate.isInitialized() || container !== sharedContainer)
            container.close() // otherwise it is testcontainer's responsibility to shutdown the container
    }

    override val dataConnector: DataConnector
        get() = DataConnector.new {
            name = UUID.randomUUID().toString()
            connectionProperties = "$jdbcUrl;user=$user;password=$password"
            println("connectionProperties=$connectionProperties")
        }
}

