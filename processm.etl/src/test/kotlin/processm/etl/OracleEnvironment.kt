package processm.etl

import org.testcontainers.containers.OracleContainer
import org.testcontainers.images.builder.Transferable
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import processm.core.logging.logger
import java.io.File
import java.sql.Connection


/**
 * An [OracleContainer] with support for SIDs
 */
class MyOracleContainer(dockerImageName: DockerImageName) : OracleContainer(dockerImageName) {

    private var _sid: String = "xe"

    fun withSid(sid: String): MyOracleContainer {
        _sid = sid
        return this
    }

    override fun getSid(): String = _sid

    override fun getJdbcUrl(): String = "jdbc:oracle:thin:$username/$password@$host:$oraclePort/$sid"
}

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
    val container: MyOracleContainer,
    val sid: String = "xe"
) : DBMSEnvironment<MyOracleContainer> {
    companion object {

        private const val DEFAULT_USER = "SYSTEM"
        private const val DEFAULT_PASSWORD = "2e3e056f2c2bf71e"
        private const val EMPTY_DB_RESOURCE = "oracle/database.tar.lzma"

        private val logger = logger()

        fun createContainer(): MyOracleContainer {
            val imageName = DockerImageName
                .parse("processm/oracle:latest")
                .asCompatibleSubstituteFor("container-registry.oracle.com/database/express:18.4.0-xe")
            val container = MyOracleContainer(imageName)
            container
                .withUsername(DEFAULT_USER)
                .withPassword(DEFAULT_PASSWORD)
                .withStartupTimeoutSeconds(500)
                .withCopyFileToContainer(MountableFile.forClasspathResource(EMPTY_DB_RESOURCE), "/database.tar.lzma")
                .withLogConsumer { frame ->
                    logger.info(frame?.utf8String?.trim())
                }
            return container
        }

        private val sharedContainer by lazy {
            val container = createContainer()
            Startables.deepStart(listOf(container)).join()
            return@lazy container
        }

        private val sakilaEnv by lazy {
            val env = OracleEnvironment(sharedContainer)
            env.configureWithScripts(
                "sakila/oracle-sakila-db/oracle-sakila-schema.sql",
                "sakila/oracle-sakila-db/oracle-sakila-insert-data.sql"
            )
            return@lazy env
        }

        private val OTSampleDBEnv by lazy {
            val env = OracleEnvironment(sharedContainer, "xepdb1")
            env.configureSampleDB()
            return@lazy env
        }

        fun getSakila(): OracleEnvironment = sakilaEnv

        fun getOTSampleDb(): OracleEnvironment = OTSampleDBEnv
    }


    fun configureWithScripts(vararg scripts: String) {
        for ((idx, script) in scripts.withIndex()) {
            val path = "/tmp/$idx.sql"
            container.copyFileToContainer(MountableFile.forClasspathResource(script), path)
            with(
                container.execInContainer(
                    "sqlplus",
                    "${container.username}/${container.password}@localhost:1521/$sid",
                    "@$path"
                )
            ) {
                logger.debug(stdout)
                logger.warn(stderr)
                check(exitCode == 0)
            }
        }
    }

    fun configureSampleDB() {
        val scriptPath = "/tmp/script.sh"

        container.copyFileToContainer(
            MountableFile.forClasspathResource("oracle/db-sample-schemas-18c.tar.gz"),
            "/tmp/sample.tar.gz"
        )
        val f = File.createTempFile("processm", null)
        f.deleteOnExit()
        val connectString = "localhost:1521/$sid"
        val sqlplus = "sqlplus 'SYS/${container.password}@$connectString'  AS SYSDBA"
        val script =
            """
#!/bin/sh
cd /opt/oracle/product/18c/dbhomeXE/md/admin/
# The following two lines enable Oracle Spatial, necessary for the OE schema
$sqlplus '@mdprivs.sql'
$sqlplus '@mdinst.sql'
cd /tmp
tar xf sample.tar.gz
cd db-sample-schemas-18c
ln -s . __SUB__CWD__
$sqlplus '@mksample.sql' '${container.password}' '${container.password}' hrpw oepw pmpw ixpw shpw bipw example temp /tmp/logs '$connectString'
            """.trimIndent()

        container.copyFileToContainer(Transferable.of(script.toByteArray()), scriptPath)

        with(container.execInContainer("sh", scriptPath)) {
            logger.debug(stdout)
            logger.warn(stderr)
            check(exitCode == 0)
        }
    }

    override val user: String
        get() = container.username
    override val password: String
        get() = container.password

    override fun connect(): Connection = container.withSid(sid).createConnection("")

    override val jdbcUrl: String
        get() = container.withSid(sid).jdbcUrl

    override fun close() {
        if (container !== sharedContainer)
            container.close() // otherwise it is testcontainer's responsibility to shutdown the container
    }

}