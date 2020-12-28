package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.logging.loggedScope
import processm.core.persistence.connection.DBCache
import processm.etl.discovery.DatabaseExplorer
import processm.etl.tracker.DatabaseChangeApplier
import processm.etl.tracker.DatabaseChangeApplier.DatabaseChangeEvent
import java.time.Instant
import java.util.*

internal class MetaModel(
    val targetDatabaseName: String,
    val dataModelId: Int,
    val metaModelReader: MetaModelReader,
    val metaModelAppender: MetaModelAppender): DatabaseChangeApplier {

    /**
     * Saves data from change events to meta model data storage.
     *
     * @param databaseChangeEvents List of database events to process.
     */
    override fun ApplyChange(databaseChangeEvents: List<DatabaseChangeEvent>) =
        transaction(DBCache.get(targetDatabaseName).database) {
            loggedScope { logger ->
                val now = Instant.now().toEpochMilli()
                // Update timestamps of old object versions
                databaseChangeEvents.filter { it.eventType == DatabaseChangeApplier.EventType.Update || it.eventType == DatabaseChangeApplier.EventType.Delete }
                    .forEach { event ->
                        metaModelAppender.updateObjectVersionEndTimestamp(event.entityId, metaModelReader.getClassId(event.entityTable), event.timestamp ?: now)
                    }
                // Insert the new object versions
                databaseChangeEvents.forEach { event ->
                    val classId = metaModelReader.getClassId(event.entityTable)
                    val objectVersionFields = event.objectData.mapKeys { (attributeName) -> metaModelReader.getAttributeId(classId, attributeName) }

                    metaModelAppender.addObjectVersion(event.entityId, classId, event.eventType,event.timestamp ?: now, objectVersionFields)
                }

                logger.info("Successfully handled ${databaseChangeEvents.count()} DB change events")
            }
        }

    /**
     * Builds set of traces related to provided case notion definition.
     *
     * @param caseNotionDefinition Object storing information about case notion.
     */
    fun buildTracesForCaseNotion(caseNotionDefinition: CaseNotionDefinition<EntityID<Int>>) = transaction(DBCache.get(targetDatabaseName).database) {
        val rootObjectIds = metaModelReader.getObjectVersionsRelatedToClass(caseNotionDefinition.rootClass)
        val traceSet = TraceSet(caseNotionDefinition, rootObjectIds)
        val caseNotionClassesQueue = ArrayDeque(setOf(caseNotionDefinition.rootClass))
        val temporaryVersions = mutableMapOf(caseNotionDefinition.rootClass to rootObjectIds.keys)
        val relatedObjectVersionsIds = rootObjectIds.mapKeys { caseNotionDefinition.rootClass to it.key }.toMutableMap()

        while (caseNotionClassesQueue.isNotEmpty()) {
            val parentClassId = caseNotionClassesQueue.pop()
            val parentClassObjectsIds = temporaryVersions[parentClassId]!!

            caseNotionDefinition.getChildren(parentClassId).keys.forEach { childClassId ->
                val relatedObjectVersions = metaModelReader.getRelatedObjectsVersions(parentClassObjectsIds, parentClassId, childClassId)

                relatedObjectVersions.forEach { (_, relatedObjectsIds) ->
                    relatedObjectsIds.forEach {(objectId, objectVersionsIds) ->
                        relatedObjectVersionsIds[childClassId to objectId] = objectVersionsIds
                    }
                }

                relatedObjectVersions.forEach { (objectId, relatedObjectsIds) ->
                    traceSet.addRelatedObjects(objectId, parentClassId, relatedObjectsIds, childClassId)
                }

                temporaryVersions[childClassId] = relatedObjectVersions.values.map {it.keys }.flatten().toSet()
                caseNotionClassesQueue.push(childClassId)
            }
            temporaryVersions.remove(parentClassId)
        }

        return@transaction traceSet
    }

    fun transformToEventsLogs(traceSet: TraceSet<String>) = transaction(DBCache.get(targetDatabaseName).database) {
        return@transaction traceSet.map { metaModelReader.getTraceData(it) }
    }

    companion object {
        fun build(targetDatabaseName: String, metaModelName: String, databaseExplorer: DatabaseExplorer): EntityID<Int> {
            val classes = databaseExplorer.getClasses()
            val relationships = databaseExplorer.getRelationships()

            return transaction(DBCache.get(targetDatabaseName).database) {
                val dataModelId = DataModels.insertAndGetId {
                    it[name] = metaModelName
                }

                val classIds = Classes
                    .batchInsert(classes) {
                        this[Classes.name] = it.name
                        this[Classes.dataModelId] = dataModelId
                    }
                    .map {
                        it[Classes.name] to it[Classes.id]
                    }
                    .toMap()

                val referencingAttributeIds = BatchInsertStatement(AttributesNames)
                    .apply {
                        classes.forEach { metaModelClass ->
                            metaModelClass.attributes.forEach { attribute ->
                                classIds[metaModelClass.name]?.let { classId ->
                                    addBatch()
                                    this[AttributesNames.name] = attribute.name
                                    this[AttributesNames.isReferencingAttribute] = attribute.isPartOfForeignKey
                                    this[AttributesNames.type] = attribute.type
                                    this[AttributesNames.classId] = classId
                                }
                            }
                        }
                        execute(this@transaction)
                    }.resultedValues
                    .orEmpty()
                    .filter { it[AttributesNames.isReferencingAttribute] }
                    .map {
                        (it [AttributesNames.classId] to it[AttributesNames.name]) to it[AttributesNames.id]
                    }
                    .toMap()

                BatchInsertStatement(Relationships).apply {
                    relationships.forEach { relationship ->
                        classIds[relationship.sourceClass.name]?.let { sourceClassId ->
                            classIds[relationship.targetClass.name]?.let { targetClassId ->
                                referencingAttributeIds[sourceClassId to relationship.sourceColumnName]?.let { referencingAttributeId ->
                                    addBatch()
                                    this[Relationships.name] = relationship.name
                                    this[Relationships.sourceClassId] = sourceClassId
                                    this[Relationships.targetClassId] = targetClassId
                                    this[Relationships.referencingAttributeNameId] = referencingAttributeId
                                }
                            }
                        }
                    }
                    execute(this@transaction)
                }

                return@transaction dataModelId
            }
        }
    }
}