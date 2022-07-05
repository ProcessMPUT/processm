package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.log.Event
import processm.core.log.Log
import processm.core.log.Trace
import processm.core.log.XESInputStream
import processm.core.log.attribute.Attribute.Companion.CONCEPT_NAME
import processm.core.log.attribute.Attribute.Companion.LIFECYCLE_MODEL
import processm.core.log.attribute.Attribute.Companion.LIFECYCLE_TRANSITION
import processm.core.log.attribute.Attribute.Companion.ORG_RESOURCE
import processm.core.log.attribute.Attribute.Companion.TIME_TIMESTAMP
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
                    LIFECYCLE_MODEL to StringAttr(LIFECYCLE_MODEL, "custom")
            )))
            traceSet.forEach { trace ->
                val traceEvents = transaction(DBCache.get(dataStoreDBName).database) { metaModelReader.getTraceData(trace) }
                val traceId = caseNotionClasses.joinToString("-") { caseNotionClassId ->
                    trace[caseNotionClassId]!!.keys.first()
                }

                yield(Trace(
                    mutableMapOf(
                        CONCEPT_NAME to StringAttr(CONCEPT_NAME, traceId)
                )))
                traceEvents.forEach { (timestamp, eventData) ->
                    val dataChanges = eventData.changes.orEmpty().map { "${it.key}: ${it.value}" }.joinToString()
                    yield(Event(
                        mutableMapOf(
                            ORG_RESOURCE to StringAttr(ORG_RESOURCE, eventData.objectId),
                            CONCEPT_NAME to StringAttr(CONCEPT_NAME, "${eventData.changeType} ${eventData.className}"),
                            LIFECYCLE_TRANSITION to StringAttr(LIFECYCLE_TRANSITION, eventData.changeType),
                            "Activity" to StringAttr("Activity", "${eventData.changeType} $dataChanges"),
                            TIME_TIMESTAMP to if (timestamp != null) DateTimeAttr(
                                TIME_TIMESTAMP,
                                Instant.ofEpochMilli(timestamp)
                            ) else NullAttr(TIME_TIMESTAMP)
                        )
                    ))
                }
            }
        }.iterator()
}
