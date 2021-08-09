package processm.etl.tracker

import processm.etl.metamodel.MetaModel
import processm.etl.metamodel.MetaModelAppender
import processm.etl.metamodel.MetaModelReader
import java.util.*
import kotlin.test.Ignore
import kotlin.test.Test

@Ignore("Exemplary use cases, not real tests")
class DebeziumChangeTrackerTest {

    val dataStoreDBName = "1c546119-96fb-47e9-a9e7-b57b07c1365e"

    @Test
    fun `extending meta model with data changes tracked in MySQL`() {
        val props = Properties()
        props.setProperty("name", "engine")
        props.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector")
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
        props.setProperty("offset.storage.file.filename", "/tmp/mysql_offsets.dat")
        props.setProperty("offset.flush.interval.ms", "60000")
        props.setProperty(
            "database.history", "io.debezium.relational.history.FileDatabaseHistory")
        props.setProperty(
            "database.history.file.filename", "dbhistory.dat")
        props.setProperty("database.server.id", "")
        props.setProperty("database.server.name", "my-app-connector")
        props.setProperty("database.hostname", "")
        props.setProperty("database.port", "3306")
        props.setProperty("database.user", "")
        props.setProperty("database.password", "")

        val trackerThread: Thread
        val dataModelId = 1
        val metaModelReader = MetaModelReader(dataModelId)
        val metaModelAppender = MetaModelAppender(metaModelReader)

        DebeziumChangeTracker(props, MetaModel(dataStoreDBName, metaModelReader, metaModelAppender)).use {
            trackerThread = Thread(it)
            trackerThread.start()
            Thread.sleep(1000000)
        }
        trackerThread.join()
    }

    @Test
    fun `extending meta model with data changes tracked in PostgreSQL`() {
        val props = Properties()
        props.setProperty("name", "engine")
        props.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
        props.setProperty("offset.storage.file.filename", "/tmp/offsets_postgresql.dat")
        props.setProperty("plugin.name", "pgoutput")
        props.setProperty("offset.flush.interval.ms", "60000")
        props.setProperty("max.batch.size", "2048")
        props.setProperty("snapshot.mode", "never")
        props.setProperty("database.dbname", "")
        props.setProperty("database.server.name", "my-app-connector")
        props.setProperty("database.hostname", "")
        props.setProperty("database.port", "5432")
        props.setProperty("database.user", "")
        props.setProperty("database.password", "")

        val trackerThread: Thread
        val dataModelId = 3
        val metaModelReader = MetaModelReader(dataModelId)
        val metaModelAppender = MetaModelAppender(metaModelReader)

        DebeziumChangeTracker(props, MetaModel(dataStoreDBName, metaModelReader, metaModelAppender)).use {
            trackerThread = Thread(it)
            trackerThread.start()
            Thread.sleep(6000000)
        }
        trackerThread.join()
    }
}
