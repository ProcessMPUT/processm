package processm.etl.tracker

import io.debezium.engine.ChangeEvent
import io.debezium.engine.DebeziumEngine
import io.debezium.engine.format.Json
import io.debezium.engine.format.KeyValueChangeEventFormat
import processm.core.logging.loggedScope
import java.io.Closeable
import java.util.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import processm.etl.tracker.DatabaseChangeApplier.*

/**
 * Listens to database data changes using Debezium library and emits events.
 *
 * @param properties Object containing connection configuration to be passed to Debezium.
 * @param changeApplier Component to save data change events to meta model data storage.
 */

class DebeziumChangeTracker(properties: Properties, private val changeApplier: DatabaseChangeApplier) : DebeziumEngine.ChangeConsumer<ChangeEvent<String, String>>, Runnable, Closeable {

    private val tracker= DebeziumEngine.create(KeyValueChangeEventFormat.of(Json::class.java, Json::class.java))
        .using(properties)
        .notifying(this)
        .build()

    override fun handleBatch(
        records: MutableList<ChangeEvent<String, String>>,
        committer: DebeziumEngine.RecordCommitter<ChangeEvent<String, String>>) {
        loggedScope { logger ->
            try {
                val databaseChangeEvents = records.fold(mutableListOf<DatabaseChangeEvent>()) { deserializedEvents, rawChangeEvent ->
                    try {
                        val databaseChangeEvent = deserializeDebeziumEvent(rawChangeEvent)
                        deserializedEvents.add(databaseChangeEvent)
                        committer.markProcessed(rawChangeEvent)
                    } catch (e: UnsupportedEventFormat) {
                        logger.warn("An event with unsupported format was received", e)
                        committer.markProcessed(rawChangeEvent)
                    } catch (e: Exception) {
                        logger.warn("An error occurred while processing DB event", e)
                        logger.debug("Key: ${rawChangeEvent.key()}\nValue: ${rawChangeEvent.value()}")
                    }
                    return@fold deserializedEvents
                }

                changeApplier.ApplyChange(databaseChangeEvents)
                committer.markBatchFinished()
            }
            catch (e: Exception) {
                logger.warn("Failed to apply changes from database", e)
            }
        }
    }

    override fun run() {
        tracker.run()
    }

    override fun close() {
        tracker.close()
    }

    private fun deserializeDebeziumEvent(changeEvent: ChangeEvent<String, String>): DatabaseChangeEvent {
        if (changeEvent.key() == null || changeEvent.value() == null) {
            throw UnsupportedEventFormat("Debezium events are expected to contain both: 'key' and 'value' fields")
        }

        val keyInfo: JsonObject = kotlinx.serialization.json.Json.decodeFromString<JsonObject>(changeEvent.key())
        val keyName = (((keyInfo["schema"] as JsonObject)["fields"] as JsonArray)[0] as JsonObject).extractNestedValue<String>("field")
        val keyValue = keyInfo.extractNestedValue<String>("payload", keyName)

        val valueInfo: JsonObject = kotlinx.serialization.json.Json.decodeFromString<JsonObject>(changeEvent.value())
        val eventType = valueInfo.extractNestedValue<String>("payload", "op").getEventTypeFromDebeziumOperation()
        val objectData = valueInfo.extractNestedValue<Map<String, String>?>("payload", "after")
        val tableName = valueInfo.extractNestedValue<String>("payload", "source", "table")
//        val isSnapshot = valueInfo.extractNestedValue<String?>("payload", "source", "snapshot")
        val timestamp = valueInfo.extractNestedValue<Long?>("payload", "ts_ms")
        val transaction = valueInfo.extractNestedValue<String?>("payload", "transaction")

        return DatabaseChangeEvent(
            entityKey = keyName,
            entityId = keyValue,
            entityTable = tableName,
            transactionId = transaction,
            timestamp = timestamp,
            eventType = eventType,
            isSnapshot = eventType == EventType.Snapshot,
            objectData = objectData ?: emptyMap()
        )
    }

    private fun String.getEventTypeFromDebeziumOperation() =
        when (this) {
            "r" -> EventType.Snapshot
            "c" -> EventType.Insert
            "u" -> EventType.Update
            "d" -> EventType.Delete
            else -> EventType.Unknown
        }

    private inline fun <reified TResult: Any?> JsonElement.extractNestedValue(vararg nestedFields: String): TResult {
        var currentElement: JsonElement? = this
        var isNestingEnded = false

        nestedFields.forEach {
            if (isNestingEnded) throw UnsupportedEventFormat("Cannot read value of nested element: ${nestedFields.joinToString()}")
            if (currentElement !is JsonObject) isNestingEnded = true
            else currentElement = (currentElement as JsonObject)[it]
        }

        if (null is TResult && currentElement is JsonNull)
        {
            return null as TResult
        }

        if (JsonElement is TResult) {
            return currentElement as TResult
        }

        val selectedElement = currentElement
                              ?: throw UnsupportedEventFormat("Non-nullable value expected at: ${nestedFields.joinToString()}")

        return when (TResult::class) {
            Int::class -> selectedElement.jsonPrimitive.int as TResult
            Long::class -> selectedElement.jsonPrimitive.long as TResult
            String::class -> selectedElement.jsonPrimitive.content as TResult
            Boolean::class -> selectedElement.jsonPrimitive.boolean as TResult
            Map::class -> selectedElement.jsonObject.map { _object -> _object.key to _object.value.toString() }.toMap() as TResult
            else -> selectedElement.jsonPrimitive.content as TResult
        }
    }
}