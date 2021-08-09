package processm.etl.flink

import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeHint
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.table.data.RowData
import org.apache.flink.util.Collector

class ArtemisSourceFunction(val topic: String, val decoder: DeserializationSchema<RowData>) : SourceFunction<RowData> {

    class SourceContextWrapper(
        val baseContext: SourceFunction.SourceContext<RowData>,
        val decoder: DeserializationSchema<RowData>
    ) :
        SourceFunction.SourceContext<Pair<String, String>> {

        // TODO I guess deserialization could read timestamp and call collectWithTimestamp on the underlying context

        override fun collect(message: Pair<String, String>?) {
            if (message != null) {
                // TODO consider message.first - either handle or stop sending it to Artemis
                decoder.deserialize(message.second.toByteArray(), object : Collector<RowData> {
                    override fun collect(record: RowData?) = baseContext.collect(record)

                    override fun close() {
                        // Ignored on purpose
                    }
                })
            }
        }

        override fun collectWithTimestamp(message: Pair<String, String>?, timestamp: Long) {
            error("Not implemented on purpose.")
        }

        override fun emitWatermark(watermark: Watermark?) = baseContext.emitWatermark(watermark)

        override fun markAsTemporarilyIdle() = baseContext.markAsTemporarilyIdle()

        override fun getCheckpointLock(): Any = baseContext.checkpointLock

        override fun close() = baseContext.close()

    }

    private val flinkArtemisSource =
        FlinkArtemisSource(topic, TypeInformation.of(object : TypeHint<Pair<String, String>>() {}))

    override fun run(sourceContext: SourceFunction.SourceContext<RowData>?) {
        if (sourceContext != null)
            flinkArtemisSource.run(SourceContextWrapper(sourceContext, decoder))
    }

    override fun cancel() {
        flinkArtemisSource.cancel()
    }


}
