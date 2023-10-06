package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.logging.loggedScope
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.AttributesNames
import processm.dbmodels.models.Classes
import processm.dbmodels.models.DataModels
import processm.dbmodels.models.Relationships
import processm.etl.discovery.DatabaseExplorer
import processm.etl.tracker.DatabaseChangeApplier
import processm.etl.tracker.DatabaseChangeApplier.DatabaseChangeEvent
import java.time.Instant

class MetaModel(
    private val dataStoreDBName: String,
    private val metaModelReader: MetaModelReader,
    private val metaModelAppender: MetaModelAppender
) : DatabaseChangeApplier {
    private val objectVersions = mutableMapOf<EntityID<Int>, MutableMap<String, List<EntityID<Int>>>>()
    private val objectRelations = mutableMapOf<Pair<EntityID<Int>, EntityID<Int>>, MutableList<Pair<String, String>>>()

    private fun addObjectVersions(objectId: String, objectClassId: EntityID<Int>, objectVersions: List<EntityID<Int>>) {
        this.objectVersions.getOrPut(objectClassId, ::mutableMapOf)[objectId] = objectVersions
    }

    private fun addObjectRelations(
        sourceObjectId: String,
        sourceObjectClassId: EntityID<Int>,
        targetObjectId: String,
        targetObjectClassId: EntityID<Int>
    ) {
        this.objectRelations
            .getOrPut(sourceObjectClassId to targetObjectClassId, ::mutableListOf)
            .add(sourceObjectId to targetObjectId)
    }

    /**
     * Saves data from change events to meta model data storage.
     *
     * @param databaseChangeEvents List of database events to process.
     */
    override fun ApplyChange(databaseChangeEvents: List<DatabaseChangeEvent>) =
        transaction(DBCache.get(dataStoreDBName).database) {
            loggedScope { logger ->
                val now = Instant.now().toEpochMilli()
                // Update timestamps of old object versions
                databaseChangeEvents.filter { it.eventType == DatabaseChangeApplier.EventType.Update || it.eventType == DatabaseChangeApplier.EventType.Delete }
                    .forEach { event ->
                        metaModelAppender.updateObjectVersionEndTimestamp(
                            event.entityId,
                            metaModelReader.getClassId(event.entityTable),
                            event.timestamp ?: now
                        )
                    }
                // Insert the new object versions
                databaseChangeEvents.forEach { event ->
                    val classId = metaModelReader.getClassId(event.entityTable)
                    val objectVersionFields = event.objectData.mapKeys { (attributeName) ->
                        metaModelReader.getAttributeId(
                            classId,
                            attributeName
                        )
                    }

                    metaModelAppender.addObjectVersion(
                        event.entityId,
                        classId,
                        event.eventType,
                        event.timestamp,
                        objectVersionFields
                    )
                }

                logger.info("Successfully handled ${databaseChangeEvents.count()} DB change events")
            }
        }

    /**
     * Returns a collection of traces built according to the provided business perspective definition.
     *
     * @param businessPerspectiveDefinition An object containing business perspective details.
     */
    fun buildTracesForBusinessPerspective(businessPerspectiveDefinition: DAGBusinessPerspectiveDefinition<EntityID<Int>>) =
        transaction(DBCache.get(dataStoreDBName).database) {
            val traceSet = TraceSet<String>(businessPerspectiveDefinition)

            businessPerspectiveDefinition.forEach { classId ->
                val objectsIds = mutableSetOf<String>()

                metaModelReader.getObjectVersionsRelatedToClass(classId).forEach { (objectId, objectVersions) ->
                    addObjectVersions(objectId, classId, objectVersions)

                    if (businessPerspectiveDefinition.isClassIncludedInCaseNotion(classId))
                        traceSet.addObjectVersions(objectId, classId, objectVersions)

                    objectsIds.add(objectId)
                }

                businessPerspectiveDefinition.getSuccessors(classId).forEach { successorClassId ->
                    val relatedObjectsVersions =
                        metaModelReader.getRelatedObjectsVersionsGroupedByObjects(objectsIds, classId, successorClassId)
                    relatedObjectsVersions.forEach {
                        it.forEach { (sourceObjectId, relatedObjectVersionsIds) ->
                            relatedObjectVersionsIds.forEach { (targetObjectId, _) ->
                                addObjectRelations(sourceObjectId, classId, targetObjectId, successorClassId)

                                if (businessPerspectiveDefinition.isClassIncludedInCaseNotion(classId))
                                    traceSet.addObjectRelations(
                                        sourceObjectId, classId, targetObjectId, successorClassId
                                    )
                            }
                        }
                    }
                }
            }

            // at this moment the traceSet contains case notions but the full data, it lacks events related to non identifying classes

            return@transaction traceSet.map { caseNotionsClasses ->
                val processedClasses = caseNotionsClasses.toMutableMap()

                // the following includes successing classes, the trace still lacks i.a. predecessing classes

                val successingClassesToBeProcessed = ArrayDeque(businessPerspectiveDefinition.caseNotionClasses)

                while (successingClassesToBeProcessed.isNotEmpty()) {
                    val currentClassId = successingClassesToBeProcessed.removeFirst()

                    businessPerspectiveDefinition.getSuccessors(currentClassId)
                        .filterNot { businessPerspectiveDefinition.isClassIncludedInCaseNotion(it) }
                        .forEach { successorClassId ->
                            val successorObjects = objectRelations[currentClassId to successorClassId]!!
                                .filter { (sourceObjectId, _) ->
                                    processedClasses[currentClassId]?.containsKey(
                                        sourceObjectId
                                    ) ?: false
                                }
                                .map { (_, targetObjectId) -> targetObjectId }

                            if (processedClasses.containsKey(successorClassId)) {
                                val currentObjects =
                                    processedClasses.getOrPut(successorClassId, { emptyMap() }).toMutableMap()
                                currentObjects.putAll(successorObjects.map {
                                    it to objectVersions[successorClassId]?.get(
                                        it
                                    ).orEmpty()
                                })
                                processedClasses[successorClassId] = currentObjects
                            } else {
                                processedClasses[successorClassId] =
                                    successorObjects.map { it to objectVersions[successorClassId]?.get(it).orEmpty() }
                                        .toMap()
                            }

                            if (successorObjects.isNotEmpty()) successingClassesToBeProcessed.addLast(successorClassId)
                        }
                }

                // the following includes predecessing classes, the trace still lacks classes independent of the case notions members (the are neither predecessing nor successing the case notion members)

                val predecessingClassesToBeProcessed = ArrayDeque(businessPerspectiveDefinition.caseNotionClasses)

                while (predecessingClassesToBeProcessed.isNotEmpty()) {
                    val currentClassId = predecessingClassesToBeProcessed.removeFirst()

                    businessPerspectiveDefinition.getPredecessors(currentClassId)
                        .filterNot { businessPerspectiveDefinition.isClassIncludedInCaseNotion(it) }
                        .forEach { predecessorClassId ->
                            val predecessorObjects = objectRelations[predecessorClassId to currentClassId]!!
                                .filter { (_, targetObjectId) ->
                                    processedClasses[currentClassId]?.containsKey(
                                        targetObjectId
                                    ) ?: false
                                }
                                .map { (sourceObjectId, _) -> sourceObjectId }

                            if (processedClasses.containsKey(predecessorClassId)) {
                                val currentObjects =
                                    processedClasses.getOrPut(predecessorClassId, { emptyMap() }).toMutableMap()
                                currentObjects.putAll(predecessorObjects.map {
                                    it to objectVersions[predecessorClassId]?.get(
                                        it
                                    ).orEmpty()
                                })
                                processedClasses[predecessorClassId] = currentObjects
                            } else {
                                processedClasses[predecessorClassId] = predecessorObjects.map {
                                    it to objectVersions[predecessorClassId]?.get(it).orEmpty()
                                }.toMap()
                            }

                            if (predecessorObjects.isNotEmpty()) predecessingClassesToBeProcessed.addLast(
                                predecessorClassId
                            )
                        }
                }

                // the following includes classes independent of case notion members

                val notYetProcessedClasses = ArrayDeque(businessPerspectiveDefinition.toList())
                val alreadyProcessedClasses = processedClasses.keys

                while (notYetProcessedClasses.isNotEmpty()) {
                    val currentClassId = notYetProcessedClasses.removeFirst()

                    businessPerspectiveDefinition.getSuccessors(currentClassId)
                        .filterNot { alreadyProcessedClasses.contains(it) }
                        .forEach { successorClassId ->
                            val successorObjects = objectRelations[currentClassId to successorClassId]!!
                                .filter { (sourceObjectId, _) ->
                                    processedClasses[currentClassId]?.containsKey(
                                        sourceObjectId
                                    ) ?: false
                                }
                                .map { (_, targetObjectId) -> targetObjectId }

                            if (processedClasses.containsKey(successorClassId)) {
                                val currentObjects =
                                    processedClasses.getOrPut(successorClassId, { emptyMap() }).toMutableMap()
                                currentObjects.putAll(successorObjects.map {
                                    it to objectVersions[successorClassId]?.get(
                                        it
                                    ).orEmpty()
                                })
                                processedClasses[successorClassId] = currentObjects
                            } else {
                                processedClasses[successorClassId] =
                                    successorObjects.map { it to objectVersions[successorClassId]?.get(it).orEmpty() }
                                        .toMap()
                            }

                            if (successorObjects.isNotEmpty()) notYetProcessedClasses.addLast(successorClassId)
                        }
                }

                return@map processedClasses
            }
        }

    companion object {
        fun build(dataStoreDBName: String, metaModelName: String, databaseExplorer: DatabaseExplorer): EntityID<Int> {
            val classes = databaseExplorer.getClasses()
            val relationships = databaseExplorer.getRelationships()

            return transaction(DBCache.get(dataStoreDBName).database) {
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

                val referencingAttributeIds = BatchInsertStatement(AttributesNames).apply {
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
                    .associate {
                        (it[AttributesNames.classId] to it[AttributesNames.name]) to it[AttributesNames.id]
                    }

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