package processm.etl

import org.testcontainers.containers.JdbcDatabaseContainer

interface DBMSEnvironmentConfigurator<Environment : DBMSEnvironment<Container>, Container : JdbcDatabaseContainer<*>> {
    fun beforeStart(environment: Environment, container: Container)
    fun afterStart(environment: Environment, container: Container)
}