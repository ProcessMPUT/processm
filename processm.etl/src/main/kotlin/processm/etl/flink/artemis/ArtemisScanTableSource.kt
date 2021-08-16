package processm.etl.flink.artemis

import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.table.connector.ChangelogMode
import org.apache.flink.table.connector.format.DecodingFormat
import org.apache.flink.table.connector.source.DynamicTableSource
import org.apache.flink.table.connector.source.ScanTableSource
import org.apache.flink.table.connector.source.SourceFunctionProvider
import org.apache.flink.table.data.RowData
import org.apache.flink.table.types.DataType


class ArtemisScanTableSource(
    val topic: String,
    val decodingFormat: DecodingFormat<DeserializationSchema<RowData>>,
    val producedDataType: DataType
) : ScanTableSource {

    override fun copy(): DynamicTableSource {
        TODO("Not yet implemented")
    }

    override fun asSummaryString(): String {
        TODO("Not yet implemented")
    }

    override fun getChangelogMode(): ChangelogMode = ChangelogMode.insertOnly()

    override fun getScanRuntimeProvider(runtimeProviderContext: ScanTableSource.ScanContext?): ScanTableSource.ScanRuntimeProvider {
        val decoder = decodingFormat.createRuntimeDecoder(runtimeProviderContext, producedDataType)

        val sourceFunction = ArtemisSourceFunction(topic, decoder)
        return SourceFunctionProvider.of(sourceFunction, false)
    }
}
