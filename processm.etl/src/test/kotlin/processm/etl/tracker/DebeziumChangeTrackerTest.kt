package processm.etl.tracker

import io.debezium.embedded.Connect
import io.debezium.engine.DebeziumEngine
import io.debezium.engine.format.Json
import org.junit.jupiter.api.Test
import processm.etl.metamodel.MetaModel
import processm.etl.metamodel.MetaModelAppender
import processm.etl.metamodel.MetaModelReader
import java.util.*
import java.util.concurrent.Executors

class DebeziumChangeTrackerTest {

//    val connectionString = "jdbc:postgresql://localhost:5432/dsroka?user=dsroka&password=dsroka"
    // PostgreSQL DvdRental
//    val connectionString = "jdbc:postgresql://postgresql.domek.ovh:5432/dvdrental?user=postgres&password=Y42ZqwGAg^ESr\$q6"
    // MySQL Employees
    val connectionString = "jdbc:mysql://mysql.domek.ovh:3306/employees?user=debezium&password=3b5N4enx23cfNDNH"
    val targetDatabaseName = "1c546119-96fb-47e9-a9e7-b57b07c1365e"

    @Test
    fun `tracking db changes`() {
    }

    @Test
    fun `extending meta model from MySQL`() {
        // Define the configuration for the Debezium Engine with MySQL connector...
        val props = Properties()
        props.setProperty("name", "engine")
        props.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector")
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
        props.setProperty("offset.storage.file.filename", "/tmp/mysql_offsets.dat")
        props.setProperty("offset.flush.interval.ms", "60000")
        /* begin connector properties */
        props.setProperty("database.hostname", "mysql.domek.ovh")
        props.setProperty("database.port", "3306")
        props.setProperty("database.user", "debezium")
        props.setProperty("database.password", "3b5N4enx23cfNDNH")
        props.setProperty("database.server.id", "85744")
        props.setProperty("database.server.name", "my-app-connector")
        props.setProperty(
            "database.history", "io.debezium.relational.history.FileDatabaseHistory")
        props.setProperty(
            "database.history.file.filename", "dbhistory.dat")

        val trackerThread: Thread
        val dataModelId = 3
        val metaModelReader = MetaModelReader(dataModelId)
        val metaModelAppender = MetaModelAppender(dataModelId, metaModelReader)
        DebeziumChangeTracker(props, MetaModel(targetDatabaseName, dataModelId, metaModelReader, metaModelAppender)).use {
            trackerThread = Thread(it)
            trackerThread.start()
            Thread.sleep(1000000)
        }

        trackerThread.join()
    }

    @Test
    fun `extending meta model from PostgreSQL`() {
        val props = Properties()
        props.setProperty("name", "engine")
        props.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
        props.setProperty("offset.storage.file.filename", "/tmp/offsets_postgresql.dat")
        props.setProperty("plugin.name", "pgoutput")
        props.setProperty("offset.flush.interval.ms", "60000")
        props.setProperty("max.batch.size", "2048")
        /* begin connector properties */
        props.setProperty("database.dbname", "dvdrental")
        props.setProperty("database.hostname", "postgresql.domek.ovh")
        props.setProperty("database.port", "5432")
        props.setProperty("database.user", "postgres")
        props.setProperty("database.password", "Y42ZqwGAg^ESr\$q6")
        props.setProperty("database.server.name", "my-app-connector")
        props.setProperty("snapshot.mode", "never")

        val trackerThread: Thread
        val dataModelId = 3
        val metaModelReader = MetaModelReader(dataModelId)
        val metaModelAppender = MetaModelAppender(dataModelId, metaModelReader)
        DebeziumChangeTracker(props, MetaModel(targetDatabaseName, dataModelId, metaModelReader, metaModelAppender)).use {
            trackerThread = Thread(it)
            trackerThread.start()
            Thread.sleep(6000000)
        }

        trackerThread.join()
    }
}