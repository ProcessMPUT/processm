package processm.etl

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName

class PostgreSQLEnvironment(
    dbName: String,
    user: String,
    password: String,
    val schemaScript: String,
    val insertScript: String
) : DBMSEnvironment<PostgreSQLContainer<*>>(
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
            .withInitScript(schemaScript)
        Startables.deepStart(listOf(container)).join()

        container.createConnection("").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { s ->
                s.execute(this::class.java.classLoader.getResource(insertScript)!!.readText())
            }
            connection.commit()
        }

        return container as PostgreSQLContainer<*>
    }

    override fun initContainer(): PostgreSQLContainer<*> {
        val imageName = DockerImageName.parse("debezium/postgres:12")
            .asCompatibleSubstituteFor("postgres")
        return PostgreSQLContainer<PostgreSQLContainer<*>>(imageName)
    }
}
