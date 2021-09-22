package processm.etl

import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.MountableFile
import processm.core.logging.logger

class MSSQLScriptConfigurator(val schemaScript: String, val insertScript: String) :
    DBMSEnvironmentConfigurator<MSSQLEnvironment, MSSQLServerContainer<*>> {
    companion object {
        private const val containerSchemaScript = "/tmp/schema.sql"
        private val logger = logger()
    }

    override fun beforeStart(environment: MSSQLEnvironment, container: MSSQLServerContainer<*>) {
        container.withCopyFileToContainer(MountableFile.forClasspathResource(schemaScript), containerSchemaScript)
    }

    override fun afterStart(environment: MSSQLEnvironment, container: MSSQLServerContainer<*>) {
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
        container.withUrlParam("database", environment.dbName)

        container.createConnection("").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { s ->
                s.execute(this::class.java.classLoader.getResource(insertScript)!!.readText())
            }
            connection.commit()
        }
    }
}

class MSSQLBackupConfigurator(val backupFile: String, val restoreCommandSuffix: String) :
    DBMSEnvironmentConfigurator<MSSQLEnvironment, MSSQLServerContainer<*>> {
    companion object {
        private const val backupFileInContainer = "/tmp/db.bak"
    }

    override fun beforeStart(environment: MSSQLEnvironment, container: MSSQLServerContainer<*>) {
        container.withCopyFileToContainer(MountableFile.forClasspathResource(backupFile), backupFileInContainer)
    }

    override fun afterStart(environment: MSSQLEnvironment, container: MSSQLServerContainer<*>) {
        // restore database WideWorldImporters from disk='/tmp/WideWorldImporters-Full.bak' with move 'WWI_Primary' to '/tmp/wwi/WideWorldImporters.mdf', move 'WWI_UserData' to '/tmp/wwi/WideWorldImporters_UserData.ndf', move 'WWI_Log' to '/tmp/wwi/WideWorldImporters.ldf', move 'WWI_InMemory_Data_1' to '/tmp/wwi/WideWorldImporters_InMemory_Data_1';
        container.createConnection("").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { s ->
                s.execute("restore database ${environment.dbName} from disk='$backupFileInContainer' $restoreCommandSuffix")
            }
            connection.commit()
        }
        container.withUrlParam("database", environment.dbName)
    }
}

class MSSQLEnvironment(
    dbName: String,
    password: String,
    val configurator: DBMSEnvironmentConfigurator<MSSQLEnvironment, MSSQLServerContainer<*>>
) : DBMSEnvironment<MSSQLServerContainer<*>>(
    dbName,
    DEFAULT_USER,
    password
) {
    companion object {
        const val DOCKER_IMAGE = "mcr.microsoft.com/mssql/server:2019-CU12-ubuntu-20.04"
        const val DEFAULT_USER = "sa"

        fun getSakila(): MSSQLEnvironment =
            MSSQLEnvironment(
                "sakila",
                "sakila_password123$",
                MSSQLScriptConfigurator(
                    "sakila/sql-server-sakila-db/sql-server-sakila-schema.sql",
                    "sakila/sql-server-sakila-db/sql-server-sakila-insert-data.sql"
                )
            )

        fun getWWI() = MSSQLEnvironment(
            "WideWorldImporters",
            "sakila_password123$",
            MSSQLBackupConfigurator(
                "mssql/WWI/WideWorldImporters-Full.bak",
                "with move 'WWI_Primary' to '/tmp/wwi/WideWorldImporters.mdf', move 'WWI_UserData' to '/tmp/wwi/WideWorldImporters_UserData.ndf', move 'WWI_Log' to '/tmp/wwi/WideWorldImporters.ldf', move 'WWI_InMemory_Data_1' to '/tmp/wwi/WideWorldImporters_InMemory_Data_1'"
            )
        )
    }

    override fun initAndRun(): MSSQLServerContainer<*> {
        val container = initContainer()
        configurator.beforeStart(this, container)
        Startables.deepStart(listOf(container)).join()
        configurator.afterStart(this, container)
        return container
    }

    override fun initContainer(): MSSQLServerContainer<*> {
        return MSSQLServerContainer<MSSQLServerContainer<*>>(DOCKER_IMAGE)
            .acceptLicense()
            .withPassword(password)
    }
}
