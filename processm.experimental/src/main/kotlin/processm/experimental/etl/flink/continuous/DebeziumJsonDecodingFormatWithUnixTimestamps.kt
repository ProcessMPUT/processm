package processm.experimental.etl.flink.continuous

import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.formats.common.TimestampFormat
import org.apache.flink.formats.json.debezium.DebeziumJsonDecodingFormat
import org.apache.flink.table.connector.source.DynamicTableSource
import org.apache.flink.table.data.RowData
import org.apache.flink.table.types.DataType

class DebeziumJsonDecodingFormatWithUnixTimestamps : DebeziumJsonDecodingFormat(false, false, TimestampFormat.SQL) {
    override fun createRuntimeDecoder(
        context: DynamicTableSource.Context?,
        physicalDataType: DataType?
    ): DeserializationSchema<RowData> {
        return super.createRuntimeDecoder(context, physicalDataType)
    }
}
