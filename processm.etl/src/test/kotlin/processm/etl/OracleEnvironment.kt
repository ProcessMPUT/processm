package processm.etl

import org.testcontainers.containers.OracleContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import processm.core.logging.logger
import java.io.File


/**
 * An [OracleContainer] with support for SIDs
 */
class MyOracleContainer(dockerImageName: DockerImageName) : OracleContainer(dockerImageName) {

    private var _sid: String = "xe"

    fun withSid(sid: String) {
        _sid = sid
    }

    override fun getSid(): String = _sid

    override fun getJdbcUrl(): String = "jdbc:oracle:thin:$username/$password@$host:$oraclePort/$sid"
}

class OracleScriptConfigurator(private vararg val scripts: String) :
    DBMSEnvironmentConfigurator<OracleEnvironment, MyOracleContainer> {
    private val scriptsInContainer = ArrayList<String>()
    override fun beforeStart(environment: OracleEnvironment, container: MyOracleContainer) {
        for ((idx, script) in scripts.withIndex()) {
            val path = "/tmp/$idx.sql"
            container.withCopyFileToContainer(MountableFile.forClasspathResource(script), path)
            scriptsInContainer.add(path)
        }
    }

    override fun afterStart(environment: OracleEnvironment, container: MyOracleContainer) {
        for (script in scriptsInContainer) {
            container.execInContainer(
                "sqlplus",
                "${container.username}/${container.password}@localhost:1521/${container.sid}",
                "@$script"
            )
        }
    }
}

class OracleSampleConfigurator : DBMSEnvironmentConfigurator<OracleEnvironment, MyOracleContainer> {
    private val scriptPath = "/tmp/script.sh"

    override fun beforeStart(environment: OracleEnvironment, container: MyOracleContainer) {
        container.withCopyFileToContainer(
            MountableFile.forClasspathResource("oracle/db-sample-schemas-18c.tar.gz"),
            "/tmp/sample.tar.gz"
        )
        val f = File.createTempFile("processm", null)
        f.deleteOnExit()
        val connectString = "localhost:1521/xepdb1"
        val sqlplus = "sqlplus 'SYS/${container.password}@$connectString'  AS SYSDBA"
        f.writeText(
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
        )
        container.withCopyFileToContainer(MountableFile.forHostPath(f.absolutePath), scriptPath)
    }

    override fun afterStart(environment: OracleEnvironment, container: MyOracleContainer) {
        container.execInContainer("sh", scriptPath)
        container.withSid("xepdb1")
    }

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
    val configurator: DBMSEnvironmentConfigurator<OracleEnvironment, MyOracleContainer>
) : DBMSEnvironment<MyOracleContainer>(
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
                OracleScriptConfigurator(
                    "sakila/oracle-sakila-db/oracle-sakila-schema.sql",
                    "sakila/oracle-sakila-db/oracle-sakila-insert-data.sql"
                )
            )

        fun getOTSampleDb(): OracleEnvironment = OracleEnvironment(OracleSampleConfigurator())
    }

    override fun initAndRun(): MyOracleContainer {
        val container = initContainer()
        configurator.beforeStart(this, container)
        Startables.deepStart(listOf(container)).join()
        configurator.afterStart(this, container)
        return container
    }

    override fun initContainer(): MyOracleContainer {
        val imageName = DockerImageName
            .parse("processm/oracle:latest")
            .asCompatibleSubstituteFor("container-registry.oracle.com/database/express:18.4.0-xe")
        val container = MyOracleContainer(imageName)
        container
            .withUsername(user)
            .withPassword(password)
            .withStartupTimeoutSeconds(500)
            .withCopyFileToContainer(MountableFile.forClasspathResource(EMPTY_DB_RESOURCE), "/database.tar.lzma")
            .withLogConsumer { frame ->
                logger.info(frame?.utf8String?.trim())
            }
        return container
    }
}
