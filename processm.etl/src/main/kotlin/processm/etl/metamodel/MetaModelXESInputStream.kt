package processm.etl.metamodel

import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.log.Event
import processm.core.log.Log
import processm.core.log.Trace
import processm.core.log.XESInputStream
import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.Attribute.LIFECYCLE_MODEL
import processm.core.log.attribute.Attribute.LIFECYCLE_TRANSITION
import processm.core.log.attribute.Attribute.ORG_RESOURCE
import processm.core.log.attribute.Attribute.TIME_TIMESTAMP
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.persistence.connection.DBCache
import java.time.Instant

/**
 * Transforms a collection of traces into a stream of XES components.
 */
class MetaModelXESInputStream(
    private val traceSet: Sequence<Set<Int>>,
    private val dataStoreDBName: String,
    dataModelId: Int
) : XESInputStream {
    private val metaModelReader = MetaModelReader(dataModelId)

    override fun iterator() =
        sequence {
            yield(Log(mutableAttributeMapOf(LIFECYCLE_MODEL to "custom")))
            traceSet.forEach { trace ->
                val traceEvents =
                    transaction(DBCache.get(dataStoreDBName).database) { metaModelReader.getTraceData(trace) }

                yield(Trace())
                traceEvents.forEach { (timestamp, eventData) ->
                    val dataChanges = eventData.changes.orEmpty().map { "${it.key}: ${it.value}" }.joinToString()
                    yield(
                        Event(
                            mutableAttributeMapOf(
                                ORG_RESOURCE to eventData.objectId,
                                CONCEPT_NAME to "${eventData.changeType} ${eventData.className}",
                                LIFECYCLE_TRANSITION to eventData.changeType,
                                "Activity" to "${eventData.changeType} $dataChanges",
                                TIME_TIMESTAMP to if (timestamp != null)
                                    Instant.ofEpochMilli(timestamp)
                                else null
                            )
                        )
                    )
                }
            }
        }.iterator()
}
