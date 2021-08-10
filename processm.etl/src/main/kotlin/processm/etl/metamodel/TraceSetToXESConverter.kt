package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.log.Trace
import processm.core.log.Event
import processm.core.log.Log
import processm.core.log.attribute.DateTimeAttr
import processm.core.log.attribute.StringAttr
import processm.core.persistence.connection.DBCache
import java.time.Instant

/**
 * Transforms a collection of traces into a collection of XES components.
 */
class TraceSetToXESConverter(private val dataStoreDBName: String, private val metaModelReader: MetaModelReader)
{
    fun convert(businessPerspective: DAGBusinessPerspectiveDefinition<EntityID<Int>>, traceSet: List<MutableMap<EntityID<Int>, Map<String, List<EntityID<Int>>>>>) =
        sequence {
            yield(Log().apply {
                setCustomAttribute(businessPerspective.caseNotionClasses.joinToString("-"), "concept:name", ::StringAttr)
            })
            traceSet.forEach { trace ->
                val traceEvents = transaction(DBCache.get(dataStoreDBName).database) { metaModelReader.getTraceData(trace) }
                val traceId = businessPerspective.caseNotionClasses.map { caseNotionClassId ->
                    trace[caseNotionClassId]!!.keys.first()
                }.joinToString("-")

                yield(Trace().apply {
                    setCustomAttribute(traceId, "concept:name", ::StringAttr)
                })
                traceEvents.forEach { (timestamp, eventData) ->
                    val dataChanges = eventData.changes.orEmpty().map { "${it.key}: ${it.value}" }.joinToString()
                    yield(Event().apply {
                        if (timestamp != null) setCustomAttribute(Instant.ofEpochMilli(timestamp), "time:timestamp", ::DateTimeAttr)
                        setCustomAttribute("${eventData.changeType} ${eventData.className}", "concept:name", ::StringAttr)
                        setCustomAttribute(eventData.objectId, "org:resource", ::StringAttr)
                        setCustomAttribute("${eventData.changeType} $dataChanges", "Activity", ::StringAttr)
                    })
                }
            }
        }
}