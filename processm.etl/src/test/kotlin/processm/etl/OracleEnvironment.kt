package processm.etl

import org.testcontainers.containers.OracleContainer
import org.testcontainers.images.builder.Transferable
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.core.logging.logger
import processm.dbmodels.models.DataConnector
import processm.etl.DBMSEnvironment.Companion.TEST_DATABASES_PATH
import java.io.File
import java.sql.Connection
import java.util.*


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

        private const val DEFAULT_USER = "C##processm"
        private const val DEFAULT_PASSWORD = "2e3e056f2c2bf71e"

        private val logger = logger()

        fun createContainer(): MyOracleContainer {
            val imageName = DockerImageName
                .parse("processm/oracle:latest")
                .asCompatibleSubstituteFor("container-registry.oracle.com/database/express:18.4.0-xe")
                .asCompatibleSubstituteFor("gvenzl/oracle-xe")
            val container = MyOracleContainer(imageName)
            container
                .withUsername(DEFAULT_USER)
                .withPassword(DEFAULT_PASSWORD)
                .withStartupTimeoutSeconds(500)
                .withFileSystemBind(TEST_DATABASES_PATH.absolutePath, "/tmp/test-databases/")
                .withLogConsumer { frame ->
                    logger.info(frame?.utf8String?.trim())
                }

            Startables.deepStart(listOf(container)).join()
            container.execInContainer(
                "sh",
                "-c",
                """echo 'CREATE USER $DEFAULT_USER IDENTIFIED BY "$DEFAULT_PASSWORD" ;' | sqlplus 'SYS/$DEFAULT_PASSWORD@localhost:1521/xe AS SYSDBA'"""
            )
            container.execInContainer(
                "sh",
                "-c",
                """echo 'GRANT ALL PRIVILEGES TO $DEFAULT_USER CONTAINER=all ;' | sqlplus 'SYS/$DEFAULT_PASSWORD@localhost:1521/xe AS SYSDBA'"""
            )
            return container
        }

        private val sharedContainerDelegate = lazy { createContainer() }
        private val sharedContainer by sharedContainerDelegate

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
        for (script in scripts) {
            with(
                container.execInContainer(
                    "sqlplus",
                    "${container.username}/${container.password}@localhost:1521/$sid",
                    "@/tmp/test-databases/$script"
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
tar xf test-databases/oracle/db-sample-schemas-18c.tar.gz
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
    override val connectionProperties: Map<String, String>
        get() = TODO("Not yet implemented")

    override val dataConnector: DataConnector
        get() = DataConnector.new {
            name = UUID.randomUUID().toString()
            connectionProperties = jdbcUrl
        }

    override fun close() {
        if (!sharedContainerDelegate.isInitialized() || container !== sharedContainer)
            container.close() // otherwise it is testcontainer's responsibility to shutdown the container
    }

}
