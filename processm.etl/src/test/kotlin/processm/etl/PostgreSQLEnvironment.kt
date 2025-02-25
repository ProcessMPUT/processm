package processm.etl

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.dbmodels.models.ConnectionProperties
import processm.dbmodels.models.ConnectionType
import processm.etl.DBMSEnvironment.Companion.TEST_DATABASES_PATH
import java.io.File

class PostgreSQLEnvironment(
    dbName: String,
    user: String,
    password: String,
    val schemaScript: String,
    val insertScript: String?
) : AbstractDBMSEnvironment<PostgreSQLContainer<*>>(
    dbName,
    user,
    password
) {
    companion object {

        const val SAKILA_SCHEMA_SCRIPT = "sakila/postgres-sakila-db/postgres-sakila-schema.sql"
        const val SAKILA_INSERT_SCRIPT = "sakila/postgres-sakila-db/postgres-sakila-insert-data.sql"

        fun getSakila(): PostgreSQLEnvironment =
            PostgreSQLEnvironment(
                "sakila",
                "postgres",
                "sakila_password",
                SAKILA_SCHEMA_SCRIPT,
                SAKILA_INSERT_SCRIPT
            )
    }

    override fun initAndRun(): PostgreSQLContainer<*> {
        val container = initContainer()
            .withDatabaseName(dbName)
            .withUsername(user)
            .withPassword(password)
        Startables.deepStart(listOf(container)).join()

        container.createConnection("").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { s ->
                s.execute(File(TEST_DATABASES_PATH, schemaScript).readText())
                if (insertScript !== null)
                    s.execute(File(TEST_DATABASES_PATH, insertScript).readText())
            }
            connection.commit()
        }

        return container as PostgreSQLContainer<*>
    }

    override val connectionProperties: ConnectionProperties
        get() = ConnectionProperties(
            ConnectionType.PostgreSql,
            server = container.host,
            port = container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
            username = user,
            password = password,
            database = dbName
        )

    override fun initContainer(): PostgreSQLContainer<*> {
        val imageName = DockerImageName.parse("debezium/postgres:16-alpine")
            .asCompatibleSubstituteFor("postgres")
        return PostgreSQLContainer(imageName)
    }
}
