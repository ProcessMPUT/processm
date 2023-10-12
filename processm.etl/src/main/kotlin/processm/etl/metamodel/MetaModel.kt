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

    /**
     * Saves data from change events to meta model data storage.
     *
     * @param databaseChangeEvents List of database events to process.
     */
    override fun applyChange(databaseChangeEvents: List<DatabaseChangeEvent>) =
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
     * Returns a collection of traces built according to the provided business perspective definition. Each trace is a set
     * of IDs for the table [processm.dbmodels.models.ObjectVersions]
     *
     * @param businessPerspectiveDefinition An object containing business perspective details.
     */
    fun buildTracesForBusinessPerspective(businessPerspectiveDefinition: DAGBusinessPerspectiveDefinition): Sequence<Set<Int>> =
        sequence {
            val query = businessPerspectiveDefinition.generateSQLquery()
            DBCache.get(dataStoreDBName).getConnection().use { connection ->
                connection.prepareStatement(query).executeQuery().use { rs ->
                    while (rs.next()) {
                        yield((rs.getArray(1).array as Array<Int>).toSet())
                    }
                }
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
                    }.associate {
                        it[Classes.name] to it[Classes.id]
                    }

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