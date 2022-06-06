package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.log.Event
import processm.core.log.Log
import processm.core.log.Trace
import processm.core.log.XESInputStream
import processm.core.log.attribute.DateTimeAttr
import processm.core.log.attribute.NullAttr
import processm.core.log.attribute.StringAttr
import processm.core.persistence.connection.DBCache
import java.time.Instant

/**
 * Transforms a collection of traces into a stream of XES components.
 */
class MetaModelXESInputStream(
    private val caseNotionClasses: List<EntityID<Int>>,
    private val traceSet: List<MutableMap<EntityID<Int>, Map<String, List<EntityID<Int>>>>>,
    private val dataStoreDBName: String,
    dataModelId: Int
) : XESInputStream
{
    private val metaModelReader = MetaModelReader(dataModelId)

    override fun iterator() =
        sequence {
            yield(Log(
                mutableMapOf(
                    "lifecycle:model" to StringAttr("lifecycle:model", "custom")
            )))
            traceSet.forEach { trace ->
                val traceEvents = transaction(DBCache.get(dataStoreDBName).database) { metaModelReader.getTraceData(trace) }
                val traceId = caseNotionClasses.joinToString("-") { caseNotionClassId ->
                    trace[caseNotionClassId]!!.keys.first()
                }

                yield(Trace(
                    mutableMapOf(
                        "concept:name" to StringAttr("concept:name", traceId)
                )))
                traceEvents.forEach { (timestamp, eventData) ->
                    val dataChanges = eventData.changes.orEmpty().map { "${it.key}: ${it.value}" }.joinToString()
                    yield(Event(
                        mutableMapOf(
                            "org:resource" to StringAttr("org:resource", eventData.objectId),
                            "concept:name" to StringAttr("concept:name", "${eventData.changeType} ${eventData.className}"),
                            "lifecycle:transition" to StringAttr("lifecycle:transition", eventData.changeType),
                            "Activity" to StringAttr("Activity", "${eventData.changeType} $dataChanges"),
                            "time:timestamp" to if (timestamp != null) DateTimeAttr("time:timestamp", Instant.ofEpochMilli(timestamp)) else NullAttr("time:timestamp")
                        )
                    ))
                }
            }
        }.iterator()
}