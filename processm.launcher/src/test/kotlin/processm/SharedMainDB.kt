package processm

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import java.util.*

object SharedMainDB {
    private fun randomMainDbName() = "processm-${UUID.randomUUID()}"

    private val sharedDbContainer: PostgreSQLContainer<*> by lazy {
        val image =
            DockerImageName.parse("timescale/timescaledb:latest-pg12-oss").asCompatibleSubstituteFor("postgres")
        //TODO investigate - it seems that if user != "postgres" processm.core.persistence.Migrator.ensureDatabaseExists fails while creating a new datastore
        val user = "postgres"
        val password = "postgres"
        val container = PostgreSQLContainer(image)
            .withDatabaseName("postgres")
            .withUsername(user)
            .withPassword(password)
            .withReuse(false)
        Startables.deepStart(listOf(container)).join()
        return@lazy container
    }


    private fun ddlQuery(query: String) =
        sharedDbContainer.createConnection("").use {
            it.prepareStatement(query).execute()
        }

    private fun createDatabase(dbName: String) = ddlQuery("create database \"$dbName\"")

    private fun jdbcUrlForDb(dbName: String): String {
        val ip = sharedDbContainer.host
        val port = sharedDbContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
        val user = sharedDbContainer.username
        val password = sharedDbContainer.password
        return "jdbc:postgresql://$ip:$port/$dbName?loggerLevel=OFF&user=$user&password=$password"
    }

    fun createNewMainDb(): String {
        val dbName = randomMainDbName()
        createDatabase(dbName)
        return jdbcUrlForDb(dbName)
    }
}