package processm.experimental.etl.flink.continuous

import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.table.connector.ChangelogMode
import org.apache.flink.table.connector.format.DecodingFormat
import org.apache.flink.table.connector.source.DynamicTableSource
import org.apache.flink.table.connector.source.ScanTableSource
import org.apache.flink.table.connector.source.SourceFunctionProvider
import org.apache.flink.table.data.RowData
import org.apache.flink.table.types.DataType

class DebeziumDynamicTableSource(
    val connector: String,
    val hostname: String,
    val port: Int,
    val database: String,
    val table: String,
    val decodingFormat: DecodingFormat<DeserializationSchema<RowData>>,
    val producedDataType: DataType,
    val pluginName: String,
    val user: String,
    val password: String,
    val stateDir: String
) : ScanTableSource {
    override fun copy(): DynamicTableSource =
        DebeziumDynamicTableSource(
            connector,
            hostname,
            port,
            database,
            table,
            decodingFormat,
            producedDataType,
            pluginName,
            user,
            password,
            stateDir
        )

    override fun asSummaryString(): String =
        "Debezium table source for database $hostname:$port/$database and user $user"

    override fun getChangelogMode(): ChangelogMode = ChangelogMode.all()

    override fun getScanRuntimeProvider(runtimeProviderContext: ScanTableSource.ScanContext?): ScanTableSource.ScanRuntimeProvider {
        val decoder = decodingFormat.createRuntimeDecoder(runtimeProviderContext, producedDataType)

        val sourceFunction = DebeziumSourceFunction(
            connector,
            hostname,
            port,
            database,
            table,
            decoder,
            pluginName,
            user,
            password,
            stateDir
        )
        return SourceFunctionProvider.of(sourceFunction, false)
    }
}
