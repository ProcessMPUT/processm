package processm.experimental.etl

import processm.dbmodels.models.ConnectionProperties
import processm.dbmodels.models.ConnectionType
import processm.dbmodels.models.DataConnector
import processm.etl.DBMSEnvironment
import java.sql.Connection
import java.util.*

class SapHanaEnvironment(val container: SapHanaSQLContainer<*>) : DBMSEnvironment<SapHanaSQLContainer<*>> {
    override val user: String
        get() = container.username
    override val password: String
        get() = container.password
    override val jdbcUrl: String
        get() = container.jdbcUrl

    override val connectionProperties: ConnectionProperties
        get() = throw NotImplementedError("Unnecessary at this stage")

    override val dataConnector: DataConnector
        get() = DataConnector.new {
            name = UUID.randomUUID().toString()
            connectionProperties = ConnectionProperties(ConnectionType.JdbcString, jdbcUrl)
        }

    override fun connect(): Connection = container.createConnection("")

    override fun close() {
        container.close()
    }
}