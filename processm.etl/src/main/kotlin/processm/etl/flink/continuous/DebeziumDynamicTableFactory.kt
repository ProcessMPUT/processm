package processm.etl.flink.continuous

import org.apache.flink.configuration.ConfigOption
import org.apache.flink.configuration.ConfigOptions
import org.apache.flink.table.connector.source.DynamicTableSource
import org.apache.flink.table.factories.DeserializationFormatFactory
import org.apache.flink.table.factories.DynamicTableFactory
import org.apache.flink.table.factories.DynamicTableSourceFactory
import org.apache.flink.table.factories.FactoryUtil
import java.io.File

class DebeziumDynamicTableFactory : DynamicTableSourceFactory {
    companion object {
        const val IDENTIFIER = "debezium"
        val CONNECTOR = ConfigOptions.key("debezium.connector").stringType().noDefaultValue()
        val HOSTNAME = ConfigOptions.key("hostname").stringType().noDefaultValue()
        val PORT = ConfigOptions.key("port").intType().noDefaultValue()
        val DATABASE = ConfigOptions.key("database").stringType().noDefaultValue()
        val TABLE = ConfigOptions.key("table").stringType().noDefaultValue()
        val FORMAT = ConfigOptions.key("format").stringType().defaultValue("debezium-json")
        val PLUGIN_NAME = ConfigOptions.key("plugin.name").stringType().noDefaultValue()
        val USER = ConfigOptions.key("user").stringType().noDefaultValue()
        val PASSWORD = ConfigOptions.key("password").stringType().noDefaultValue()
        val STATE_DIR = ConfigOptions.key("state-dir").stringType().noDefaultValue()
    }

    override fun factoryIdentifier(): String = IDENTIFIER

    override fun requiredOptions(): MutableSet<ConfigOption<*>> = mutableSetOf(
        CONNECTOR, HOSTNAME, PORT, DATABASE, TABLE, FORMAT, PLUGIN_NAME, USER, PASSWORD
    )

    override fun optionalOptions(): MutableSet<ConfigOption<*>> = mutableSetOf()

    override fun createDynamicTableSource(context: DynamicTableFactory.Context?): DynamicTableSource {
        val helper = FactoryUtil.createTableFactoryHelper(this, context)

        val tableOptions = helper.options

        val connector = tableOptions.get(CONNECTOR)
        val hostname = tableOptions.get(HOSTNAME)
        val port = tableOptions.get(PORT)
        val database = tableOptions.get(DATABASE)
        val table = tableOptions.get(TABLE)

        val physicalDataType = context!!.catalogTable.resolvedSchema.toPhysicalRowDataType()
        val valueDecodingFormat = helper.discoverDecodingFormat(
            DeserializationFormatFactory::class.java, FORMAT
        )

        val pluginName = tableOptions.get(PLUGIN_NAME)
        val user = tableOptions.get(USER)
        val password = tableOptions.get(PASSWORD)
        val stateDir = tableOptions.get(STATE_DIR)

        return DebeziumDynamicTableSource(
            connector,
            hostname,
            port,
            database,
            table,
            valueDecodingFormat,
            physicalDataType,
            pluginName,
            user,
            password,
            stateDir
        )
    }
}
