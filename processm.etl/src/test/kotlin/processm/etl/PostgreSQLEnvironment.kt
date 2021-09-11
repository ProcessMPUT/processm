package processm.etl

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class PostgreSQLEnvironment(
    dbName: String,
    user: String,
    password: String,
    schemaScript: String,
    insertScript: String
) : DBMSEnvironment<PostgreSQLContainer<*>>(
    dbName,
    user,
    password,
    schemaScript,
    insertScript
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

    override fun initContainer(): PostgreSQLContainer<*> {
        val imageName = DockerImageName.parse("debezium/postgres:12")
            .asCompatibleSubstituteFor("postgres")
        return PostgreSQLContainer<PostgreSQLContainer<*>>(imageName)
    }
}
