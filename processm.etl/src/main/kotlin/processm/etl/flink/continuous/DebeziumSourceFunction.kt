package processm.etl.flink.continuous

import io.debezium.engine.ChangeEvent
import io.debezium.engine.DebeziumEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.table.data.RowData
import org.apache.flink.util.Collector
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore

class DebeziumSourceFunction(
    val connector: String,
    val hostname: String,
    val port: Int,
    val database: String,
    val table: String,
    val decoder: DeserializationSchema<RowData>,
    val pluginName: String,
    val user: String,
    val password: String,
    val stateDir: String
) : SourceFunction<RowData> {

    private val cancelSemaphore = Semaphore(0)
    private val queue =
        LinkedBlockingQueue<Pair<ChangeEvent<String, String>, DebeziumEngine.RecordCommitter<ChangeEvent<String, String>>>>()

    override fun run(ctx: SourceFunction.SourceContext<RowData>?) {
        ctx ?: return

        DebeziumCache.register(
            key = DebeziumCache.Key(connector, hostname, port, database, user, password, pluginName, stateDir),
            table = table,
            consumer = object : DebeziumCache.Consumer {
                override fun accept(
                    record: ChangeEvent<String, String>,
                    committer: DebeziumEngine.RecordCommitter<ChangeEvent<String, String>>
                ) {
                    queue.offer(record to committer)
                }

                override fun stop() {
                    cancelSemaphore.release()
                }
            }
        )

        try {
            while (true) {
                val (record, committer) = queue.take()
                // Log Sequence Number (LSN).
                // LSNs in PostgreSQL never wrap [1].
                // [1] https://www.postgresql.org/docs/13/wal-internals.html
                // [2] https://debezium.io/documentation/reference/connectors/postgresql.html#postgresql-meta-information
                val lsn =
                    Json.parseToJsonElement(record.value()).jsonObject["source"]!!.jsonObject["lsn"]!!.jsonPrimitive.long
                //val ts_ms = Json.parseToJsonElement(message).jsonObject["ts_ms"]!!.jsonPrimitive.long / 1000L
                decoder.deserialize(record.value().toByteArray(), object : Collector<RowData> {
                    override fun collect(row: RowData?) {
                        row ?: return
                        ctx.collectWithTimestamp(row, lsn)
                    }

                    override fun close() = Unit
                })

                if (queue.isEmpty()) {
                    ctx.emitWatermark(Watermark(lsn))
                    ctx.markAsTemporarilyIdle()
                }

                committer.markProcessed(record)
            }
        } catch (_: InterruptedException) {
            // Flink interrupts this thread just after returning from cancel()
        }
    }

    override fun cancel() {
        DebeziumCache.unregister(
            key = DebeziumCache.Key(connector, hostname, port, database, user, password, pluginName, stateDir),
            table = table
        )
        cancelSemaphore.acquire()
    }
}
