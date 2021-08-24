package processm.etl

import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.MountableFile

class MSSQLEnvironment(
    dbName: String,
    password: String,
    schemaScript: String,
    insertScript: String
) : DBMSEnvironment<MSSQLServerContainer<*>>(
    dbName,
    DEFAULT_USER,
    password,
    schemaScript,
    insertScript
) {
    companion object {
        const val DOCKER_IMAGE = "mcr.microsoft.com/mssql/server:2019-CU12-ubuntu-20.04"
        const val DEFAULT_USER = "sa"

        fun getSakila(): MSSQLEnvironment =
            MSSQLEnvironment(
                "sakila",
                "sakila_password123$",
                "sakila/sql-server-sakila-db/sql-server-sakila-schema.sql",
                "sakila/sql-server-sakila-db/sql-server-sakila-insert-data.sql"
            )
    }

    override fun initAndRun(): MSSQLServerContainer<*> {
        val containerSchemaScript = "/tmp/schema.sql"

        val container = initContainer()
            .withPassword(password)
            .withCopyFileToContainer(MountableFile.forClasspathResource(schemaScript), containerSchemaScript)
        Startables.deepStart(listOf(container)).join()

        with(container.execInContainer("/opt/mssql-tools/bin/sqlcmd", "-U", user, "-P", password, "-i", containerSchemaScript)) {
            println(stdout)
            System.err.println(stderr)
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

        return container as MSSQLServerContainer<*>
    }

    override fun initContainer(): MSSQLServerContainer<*> {
        return MSSQLServerContainer<MSSQLServerContainer<*>>(DOCKER_IMAGE).acceptLicense()
    }
}
