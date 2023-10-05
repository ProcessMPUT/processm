package processm.etl

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.etl.DBMSEnvironment.Companion.TEST_DATABASES_PATH
import java.io.File

class PostgreSQLEnvironment(
    dbName: String,
    user: String,
    password: String,
    val schemaScript: String,
    val insertScript: String
) : AbstractDBMSEnvironment<PostgreSQLContainer<*>>(
    dbName,
    user,
    password
) {
    companion object {
        fun getSakila(): PostgreSQLEnvironment =
            PostgreSQLEnvironment(
                "sakila",
                "postgres",
                "sakila_password",
                "sakila/postgres-sakila-db/postgres-sakila-schema.sql",
                "sakila/postgres-sakila-db/postgres-sakila-insert-data.sql"
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
                s.execute(File(TEST_DATABASES_PATH, insertScript).readText())
            }
            connection.commit()
        }

        return container as PostgreSQLContainer<*>
    }

    override fun initContainer(): PostgreSQLContainer<*> {
        val imageName = DockerImageName.parse("debezium/postgres:12")
            .asCompatibleSubstituteFor("postgres")
        return PostgreSQLContainer(imageName)
    }

    fun clearAllData() {
        container.createConnection("").use { connection ->
            connection.autoCommit = false
            val rsTables =
                connection.prepareStatement("SELECT table_name FROM information_schema.tables where table_schema='public' and table_type='BASE TABLE'")
                    .executeQuery()
            val tables = ArrayList<String>()
            while (rsTables.next()) {
                tables.add(rsTables.getString(1))
            }
            connection.prepareStatement(
                tables.joinToString(
                    separator = ", ",
                    prefix = "truncate "
                ) { """"$it"""" }.also { println(it) }) //TODO sql escape
                .execute()
            connection.commit()
        }
    }

    fun populate() {
        container.createConnection("").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { s ->
                s.execute(File(TEST_DATABASES_PATH, insertScript).readText())
            }
            connection.commit()
        }
    }
}
