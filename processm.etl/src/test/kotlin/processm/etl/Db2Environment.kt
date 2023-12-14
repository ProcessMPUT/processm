package processm.etl

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Db2Container
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.lifecycle.Startables
import org.testcontainers.shaded.org.apache.commons.io.IOUtils
import org.testcontainers.utility.DockerImageName
import processm.core.logging.logger
import processm.dbmodels.models.DataConnector
import processm.etl.DBMSEnvironment.Companion.TEST_DATABASES_PATH
import processm.etl.Db2Environment.Companion.groupInserts
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * DB2 as a component in integration testing sucks:
 * 1. Creating an empty database takes a lot of time, see https://stackoverflow.com/questions/67584478/db2-create-database-takes-long-time
 * 2. Issuing a stream of inserts takes an enormous amount of time
 * 3. The persistent directory of the Docker image contains files with various ownerships and permissions, and it is important
 * to preserve them, e.g., because some of the files are executable
 * 4. For some reason the database directory cannot reside on tmpfs, which is unfortunate, as it would increase performance.
 * It can be cheated by creating an image in a file on a tmpfs mount, formatting the image into ext3, mounting that image
 * and binding that mount to the container - unfortunately, this requires elevated privileges.
 *
 * To address these issues the following approach was implemented:
 * [Db2Environment] does not get SQL scripts as constructor parameters. Instead, it expects a database dump, which can
 * be generated using [prepare]. Note, that [prepare] should never be called as a part of a normal workflow (e.g., in tests).
 * Its purpose is to be called manually to create and populate a database from SQL scripts, and then to export the database as a TGZ file.
 * This TGZ should then be placed as a JVM resource accessible for [Db2Environment] and the path to it should be passed as
 * [persistentDatabaseResourcePath] in the constructor. See also [processm.etl.jdbc.Db2SakilaPrepare]
 *
 * To enable ownership- and permissions-preserving database copy, an Docker image `processm/db2` was created as a derived image from `ibmcom/db2:11.5.5.1`
 * with two additional scripts: one serving as an entrypoint and responsible for unpacking the TGZ and starting the original entry point,
 * and the other responsible for packing the DB as a TGZ file to be copied out of the container in [prepare].
 * The image is built by Maven in the generate-test-resources phase
 *
 * The database copy can be used in a manually created container (e.g., for debug/development purposes) using the following command
 * (values in the angle brackets are to be replaced with the same values as used during creation of the DB copy):
 * ```
 * docker run -ti -p 50000:50000 --privileged=true -e LICENSE=accept -e 'DB2INSTANCE=<username>' -e DBNAME=<database> -e 'DB2INST1_PASSWORD=<password>' -e AUTOCONFIG=false -e ARCHIVE_LOGS=false -v <path_to_database.tgz>:/database.tgz   --name mydb2 processm/db2:latest
 * ```
 *
 * Connecting to a DB2 instance and issuing SQL commands seems to be an issue in and of itself.
 * IBM Data Studio, while being very large, rather crude, and requiring free registration with IBM to obtain and seems to work.
 * DBeaver also seems to do the trick using DB2 LUW driver.
 *
 * To alleviate the issue with inserts taking a long time, [groupInserts] was implemented. It is a very crude and naive
 * approach to group multiple consecutive inserts to the same table into a single, long insert with multiple rows.
 */
class Db2Environment(
    dbName: String,
    user: String,
    password: String,
    val persistentDatabaseResourcePath: String
) : AbstractDBMSEnvironment<Db2Container>(
    dbName,
    user,
    password
) {

    companion object {
        private val logger = logger()

        fun getSakila(): Db2Environment =
            Db2Environment(
                "sakila",
                "postgres",
                "sakila_password",
                "sakila/db2-sakila-db/database.tar.xz"
            )

        fun getGSDB(): Db2Environment =
            Db2Environment(
                "GSDB",
                "sales",
                "salespw",
                "gsdb/database.tar.xz"
            )

        /**
         * Quick and dirty converter from multiple consecutive inserts into a single multi-row insert.
         * Greatly improves efficiency.
         */
        fun groupInserts(insertScript: String): List<String> {
            val resource = ScriptUtils::class.java.classLoader.getResource(insertScript)
            val script: String = IOUtils.toString(resource, StandardCharsets.UTF_8)
            val statements = ArrayList<String>()

            ScriptUtils.splitSqlScript(
                insertScript,
                script,
                ";",
                "--",
                "/*",
                "*/",
                statements
            )

            val groupedStatements = ArrayList<String>()
            val insertRegex = Regex("^(insert\\s+into[^(]+\\(.*?\\)\\s*values)(.*)$", RegexOption.IGNORE_CASE)
            var prefix: String? = null
            val suffixes = ArrayList<String>()

            fun flushSuffixes() {
                if (suffixes.isNotEmpty()) {
                    check(prefix != null)
                    groupedStatements.add(prefix + suffixes.joinToString(", "))
                    suffixes.clear()
                    prefix = null
                }
            }

            for (s in statements) {
                val m = insertRegex.matchEntire(s)
                if (m == null) {
                    flushSuffixes()
                    groupedStatements.add(s)
                    continue
                }
                val currentPrefix = m.groupValues[1]
                val currentSuffix = m.groupValues[2]
                if (currentPrefix != prefix) {
                    flushSuffixes()
                    prefix = currentPrefix
                }
                suffixes.add(currentSuffix)
            }
            flushSuffixes()

            return groupedStatements
        }
    }


    /**
     * Never, ever call this function as a part of a normal workflow. For more information see the class documentation
     */
    fun prepare(
        configurator: DBMSEnvironmentConfigurator<Db2Environment, Db2Container>,
        persistentDatabaseTargetPath: String
    ) {
        val container = initContainer()

        configurator.beforeStart(this, container)
        Startables.deepStart(listOf(container)).join()
        configurator.afterStart(this, container)

        container.execInContainer("bash", "/packdb.sh")
        container.copyFileFromContainer("/database.tar.xz", persistentDatabaseTargetPath)

        container.stop()
    }

    override fun initAndRun(): Db2Container {
        val container = initContainer()
        Startables.deepStart(listOf(container)).join()

        return container
    }

    override val connectionProperties: Map<String, String>
        get() = TODO("Not yet implemented")

    override fun initContainer(): Db2Container {
        val imageName = DockerImageName
            .parse("processm/db2:latest")
            .asCompatibleSubstituteFor("ibmcom/db2")
        return Db2Container(imageName)
            .acceptLicense()
            .withDatabaseName(dbName)
            .withUsername(user)
            .withPassword(password)
            .withEnv("AUTOCONFIG", "false")
            .withEnv("ARCHIVE_LOGS", "false")
            .withEnv("DB_FILE", persistentDatabaseResourcePath)
            .withFileSystemBind(TEST_DATABASES_PATH.absolutePath, "/tmp/test-databases/", BindMode.READ_ONLY)
            .withLogConsumer { frame ->
                logger.info(frame?.utf8String?.trim())
            }
    }

    override val dataConnector: DataConnector
        get() = DataConnector.new {
            name = UUID.randomUUID().toString()
            connectionProperties = "$jdbcUrl:user=$user;password=$password;"
        }
}
