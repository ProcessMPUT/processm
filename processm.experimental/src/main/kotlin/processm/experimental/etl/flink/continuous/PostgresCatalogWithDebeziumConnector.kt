package processm.experimental.etl.flink.continuous

import org.apache.flink.connector.jdbc.catalog.PostgresCatalog
import org.apache.flink.connector.jdbc.catalog.PostgresTablePath
import org.apache.flink.table.catalog.CatalogBaseTable
import org.apache.flink.table.catalog.CatalogTable
import org.apache.flink.table.catalog.ObjectPath
import org.apache.flink.table.factories.Factory
import org.apache.flink.table.factories.FactoryUtil
import java.util.*
import kotlin.collections.HashMap

class PostgresCatalogWithDebeziumConnector(
    catalogName: String,
    val hostname: String,
    val port: Int,
    database: String,
    username: String,
    pwd: String,
    val stateDir: String
) : PostgresCatalog(catalogName, database, username, pwd, "jdbc:postgresql://$hostname:$port") {
    override fun getTable(tablePath: ObjectPath?): CatalogBaseTable {
        requireNotNull(tablePath)

        val baseTable = super.getTable(tablePath)
        val tableName = PostgresTablePath.fromFlinkTableName(tablePath.objectName).fullPath

        // change options to use DebeziumDynamicTableFactory
        val options = HashMap<String, String>().apply {
            put(FactoryUtil.CONNECTOR.key(), DebeziumDynamicTableFactory.IDENTIFIER)
            put(DebeziumDynamicTableFactory.CONNECTOR.key(), "io.debezium.connector.postgresql.PostgresConnector")
            put(DebeziumDynamicTableFactory.HOSTNAME.key(), hostname)
            put(DebeziumDynamicTableFactory.PORT.key(), port.toString())
            put(DebeziumDynamicTableFactory.DATABASE.key(), defaultDatabase)
            put(DebeziumDynamicTableFactory.TABLE.key(), tableName)
            put(DebeziumDynamicTableFactory.PLUGIN_NAME.key(), "pgoutput")
            put(DebeziumDynamicTableFactory.USER.key(), username)
            put(DebeziumDynamicTableFactory.PASSWORD.key(), password)
            put(DebeziumDynamicTableFactory.STATE_DIR.key(), stateDir)
        }

        return CatalogTable.of(baseTable.unresolvedSchema, baseTable.comment, emptyList(), options)
    }

    override fun getFactory(): Optional<Factory> =
        Optional.of(DebeziumDynamicTableFactory())
}
