package processm.etl

import org.testcontainers.containers.OracleContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import processm.core.logging.logger

/**
 * A test environment with Oracle Express.
 *
 * Instead of creating a new database each time, it restores a backup of an empty, preconfigured DB.
 * The file with it is quite large, but bringing up the DB this way takes around 7x less time than when bringing it up from scratch.
 * Currently, password is hardcoded, but it is possible to change it by calling `setPassword.sh` script.
 *
 * For the Sakila DB, inserting the data takes a non-negligible amount of time, but it is not that long and
 * it seems to me that making such a large backup for every database would be cumbersome.
 * Grouping multiple inserts in a similar way as in [Db2Environment] doesn't seem to help, to the point of being actually slower.
 */
class OracleEnvironment(
    val schemaScript: String,
    val insertScript: String
) : DBMSEnvironment<OracleContainer>(
    "",
    DEFAULT_USER,
    DEFAULT_PASSWORD
) {
    companion object {

        private const val DEFAULT_USER = "SYSTEM"
        private const val DEFAULT_PASSWORD = "2e3e056f2c2bf71e"
        private const val EMPTY_DB_RESOURCE = "oracle/database.tar.lzma"

        private val logger = logger()

        fun getSakila(): OracleEnvironment =
            OracleEnvironment(
                "sakila/oracle-sakila-db/oracle-sakila-schema.sql",
                "sakila/oracle-sakila-db/oracle-sakila-insert-data.sql"
            )
    }

    override fun initAndRun(): OracleContainer {
        val container = initContainer()
            .withCopyFileToContainer(MountableFile.forClasspathResource(schemaScript), "/schema.sql")
            .withCopyFileToContainer(MountableFile.forClasspathResource(insertScript), "/insert.sql")

        Startables.deepStart(listOf(container)).join()

        container.execInContainer(
            "sqlplus",
            "${container.username}/${container.password}@localhost:1521/${container.sid}",
            "@/schema.sql"
        )

        container.execInContainer(
            "sqlplus",
            "${container.username}/${container.password}@localhost:1521/${container.sid}",
            "@/insert.sql"
        )

        return container as OracleContainer
    }

    override fun initContainer(): OracleContainer {
        val imageName = DockerImageName
            .parse("processm/oracle:latest")
            .asCompatibleSubstituteFor("container-registry.oracle.com/database/express:18.4.0-xe")
        return OracleContainer(imageName)
            .withUsername(user)
            .withPassword(password)
            .withStartupTimeoutSeconds(500)
            .withCopyFileToContainer(MountableFile.forClasspathResource(EMPTY_DB_RESOURCE), "/database.tar.lzma")
            .withLogConsumer { frame ->
                logger.info(frame?.utf8String?.trim())
            }
    }
}
