package processm.etl.flink.continuous

import io.debezium.engine.ChangeEvent
import io.debezium.engine.DebeziumEngine
import io.debezium.engine.format.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import processm.core.logging.loggedScope
import processm.etl.flink.continuous.DebeziumCache.Consumer
import processm.etl.flink.continuous.DebeziumCache.DELAY_BEFORE_START
import processm.etl.flink.continuous.DebeziumCache.Key
import processm.etl.flink.continuous.DebeziumCache.register
import processm.etl.flink.continuous.DebeziumCache.unregister
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Keeps at most one instance of Debezium per [Key.stateDir]. Removes the instance if unused. A single instance may serve
 * several tables in the same database. Each time a new table is registered using [register], the existing instance of
 * Debezium if any is stopped and a new one with the updated configuration is scheduled for execution. The new instance
 * of Debezium does not immediately run but waits [DELAY_BEFORE_START]ms before running to avoid restarts caused by
 * pending registrations. Restarting Debezium may be expensive. The consumer that wants to stop listening to Debezium
 * messages for a certain table should [unregister] and wait for [Consumer.stop] call before clean up. The call to
 * [Consumer.stop] indicates that no more messages for that table will be sent.
 * This class is thread-safe. The [Consumer.accept] method will be called using different thread than the registering
 * thread and the implementations of [Consumer] should be properly synchronized.
 */
object DebeziumCache {

    private const val PUBLICATION_NAME = "processm_publication"
    private const val SLOT_NAME = "processm"

    /**
     * Milliseconds.
     */
    private const val DELAY_BEFORE_START = 500L

    private val executor = Executors.newScheduledThreadPool(1)

    private val cache = ConcurrentHashMap<String, Value>()

    fun register(key: Key, table: String, consumer: Consumer) {
        loggedScope {
            cache.compute(key.stateDir) { _, old ->
                // synchronized context thanks to ConcurrentHashMap
                val tables =
                    if (old !== null) {
                        check(table !in old.tables) { "Table $table has been already registered." }
                        old.stop()
                        old.tables
                    } else {
                        mutableMapOf()
                    }
                tables[table] = consumer
                createEngine(key, tables).also {
                    assert(table in it.tables)
                }
            }
        }
    }

    fun unregister(key: Key, table: String) {
        loggedScope {
            cache.compute(key.stateDir) { _, old ->
                // synchronized context thanks to ConcurrentHashMap
                checkNotNull(old) { "Debezium instance is not found for the given key." }
                check(table in old.tables) { "Table $table is not registered." }
                old.stop()
                old.tables.remove(table)!!.stop()
                assert(table !in old.tables)

                if (old.tables.isNotEmpty()) createEngine(key, old.tables)
                else null
            }
        }
    }

    private fun createEngine(key: Key, tables: MutableMap<String, Consumer>): Value {
        val stateFile = createStateFile(key.stateDir)
        val props = Properties().apply {
            setProperty("name", "engine"); // TODO: do we need this?
            setProperty("connector.class", key.connector)
            setProperty("database.hostname", key.hostname)
            setProperty("database.port", key.port.toString())
            setProperty("database.dbname", key.database)
            setProperty("database.user", key.user)
            setProperty("database.password", key.password)
            setProperty("database.server.name", key.database)
            setProperty("decimal.handling.mode", "double")
            setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
            setProperty("offset.storage.file.filename", stateFile.absolutePath);
            setProperty("offset.flush.interval.ms", "1000") // 60000
            setProperty("plugin.name", key.pluginName)
            setProperty("publication.name", PUBLICATION_NAME)
            setProperty("slot.name", SLOT_NAME)
            setProperty("table.include.list", tables.keys.joinToString(","))
            setProperty("time.precision.mode", "adaptive_time_microseconds")
            setProperty("value.converter.schemas.enable", "false")

        }

        val debezium = DebeziumEngine
            .create(Json::class.java)
            .using(props)
            .using(io.debezium.engine.spi.OffsetCommitPolicy.always())
            .notifying(DebeziumConsumer(tables))
            .build()

        return Value(tables, debezium, executor.schedule(debezium, DELAY_BEFORE_START, TimeUnit.MILLISECONDS))
    }

    private fun createStateFile(baseStateDir: String): File = File(baseStateDir, "debezium")

    data class Key(
        val connector: String,
        val hostname: String,
        val port: Int,
        val database: String,
        val user: String,
        val password: String,
        val pluginName: String,
        val stateDir: String
    )

    private class Value(
        val tables: MutableMap<String, Consumer>,
        val engine: DebeziumEngine<ChangeEvent<String, String>>,
        val task: ScheduledFuture<*>
    ) {
        fun stop() {
            task.cancel(false)
            engine.close()
            try {
                // await termination
                task.get()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    interface Consumer {
        fun accept(
            record: ChangeEvent<String, String>,
            committer: DebeziumEngine.RecordCommitter<ChangeEvent<String, String>>
        )

        fun stop()
    }

    private class DebeziumConsumer(
        val tables: Map<String, Consumer>
    ) : DebeziumEngine.ChangeConsumer<ChangeEvent<String, String>> {

        override fun handleBatch(
            records: MutableList<ChangeEvent<String, String>>?,
            committer: DebeziumEngine.RecordCommitter<ChangeEvent<String, String>>?
        ) {
            checkNotNull(records)
            checkNotNull(committer)

            for (record in records) {
                val json = kotlinx.serialization.json.Json.parseToJsonElement(record.value().toString())
                val table: String
                try {
                    val source = json.jsonObject["source"]?.jsonObject
                    val tableName = source?.get("table")?.jsonPrimitive?.content ?: return
                    val schema = source["schema"]?.jsonPrimitive?.content
                    table = if (schema !== null) "$schema.$tableName" else tableName
                } catch (_: IllegalArgumentException) {
                    return
                }
                tables[table]?.accept(record, committer) ?: throw IllegalArgumentException("Table $table is not found")
            }
        }
    }
}
