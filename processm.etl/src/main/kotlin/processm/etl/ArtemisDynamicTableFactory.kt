package processm.etl

import org.apache.flink.configuration.ConfigOption
import org.apache.flink.configuration.ConfigOptions
import org.apache.flink.table.factories.DeserializationFormatFactory
import org.apache.flink.table.factories.DynamicTableFactory
import org.apache.flink.table.factories.DynamicTableSourceFactory
import org.apache.flink.table.factories.FactoryUtil

class ArtemisDynamicTableFactory : DynamicTableSourceFactory {

    companion object {
        const val IDENTIFIER = "artemis"
        val TOPIC: ConfigOption<String> = ConfigOptions.key("topic")
            .stringType()
            .noDefaultValue()
            .withDescription("Topic names from which the table is read.")
    }

    override fun factoryIdentifier(): String {
        return IDENTIFIER
    }

    override fun requiredOptions(): MutableSet<ConfigOption<*>> {
        return mutableSetOf(TOPIC)
    }

    override fun optionalOptions(): MutableSet<ConfigOption<*>> {
        return mutableSetOf()
    }

    override fun createDynamicTableSource(context: DynamicTableFactory.Context?): ArtemisScanTableSource {
        val helper = FactoryUtil.createTableFactoryHelper(this, context)

        val tableOptions = helper.options

        val physicalDataType = context!!.catalogTable.resolvedSchema.toPhysicalRowDataType()
        val valueDecodingFormat = helper.discoverDecodingFormat(
            DeserializationFormatFactory::class.java, FactoryUtil.FORMAT
        )

        val topic = tableOptions.get(TOPIC)

        return ArtemisScanTableSource(topic, valueDecodingFormat, physicalDataType)
    }
}