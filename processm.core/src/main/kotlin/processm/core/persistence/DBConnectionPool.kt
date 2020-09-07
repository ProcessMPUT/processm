package processm.core.persistence

import org.apache.commons.dbcp2.DriverManagerConnectionFactory
import org.apache.commons.dbcp2.PoolableConnection
import org.apache.commons.dbcp2.PoolableConnectionFactory
import org.apache.commons.dbcp2.PoolingDataSource
import org.apache.commons.pool2.ObjectPool
import org.apache.commons.pool2.impl.GenericObjectPool
import org.jetbrains.exposed.sql.Database
import processm.core.persistence.connection.DatabaseChecker
import java.sql.Connection
import javax.management.ObjectName
import javax.sql.DataSource

/**
 * Manages the database connection pool. It provides three access methods:
 * * JDBC [Connection]
 * * JDBC [DataSource]
 * * JetBrains Exposed [Database]
 */
class DBConnectionPool(databaseName: String) {
    /**
     * Selected `databaseName` connection URL supported by JDBC.
     */
    private val connectionURL = dbConfig.switchDatabaseURL(databaseName)

    private val connectionPool: ObjectPool<PoolableConnection> by lazy {
        migrator.migrate(databaseName)

        // First, we'll create a ConnectionFactory that the
        // pool will use to create Connections.
        // We'll use the DriverManagerConnectionFactory,
        // using the connect string passed in the command line
        // arguments.
        val connectionFactory = DriverManagerConnectionFactory(connectionURL)

        // Next we'll create the PoolableConnectionFactory, which wraps
        // the "real" Connections created by the ConnectionFactory with
        // the classes that implement the pooling functionality.
        val jmxName = ObjectName("${jmxDomain}:name=DBConnectionPool(${databaseName})")
        val poolableConnectionFactory = PoolableConnectionFactory(connectionFactory, jmxName)
        poolableConnectionFactory.setConnectionInitSql(listOf("SET timezone='UTC'"))

        // Now we'll need a ObjectPool that serves as the actual pool of connections.
        // We'll use a GenericObjectPool instance, although any ObjectPool implementation will suffice.
        val connectionPool = GenericObjectPool(poolableConnectionFactory)

        // Set the factory's pool property to the owning pool
        poolableConnectionFactory.pool = connectionPool

        connectionPool
    }

    /**
     * Returns a connection from the pool or creates new if necessary. The invoker is required to call close()
     * on the received object in order to return this connection to the pool.
     */
    fun getConnection(): Connection = getDataSource().connection

    /**
     * Returns a data source associated with a connection from the pool or creates new if necessary. The invoker
     * is required to call close() on the received object.connection property in order to return this connection
     * to the pool.
     */
    fun getDataSource(): DataSource = PoolingDataSource<PoolableConnection>(connectionPool)

    /**
     * Database object for transactions managed by org.jetbrains.exposed library.
     */
    val database: Database by lazy {
        Database.connect(getDataSource())
    }

    companion object {
        private const val jmxDomain = "processm"
        private val dbConfig = DatabaseChecker
        private val migrator = Migrator
    }
}