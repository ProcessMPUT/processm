package processm.etl.metamodel

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import processm.etl.tracker.DatabaseChangeApplier
import processm.etl.tracker.DatabaseChangeApplier.DatabaseChangeEvent
import processm.helpers.mapToSet
import processm.logging.debug
import processm.logging.loggedScope
import processm.logging.logger
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

class LogGeneratingDatabaseChangeApplier(
    val dataStoreDBName: String,
    val metaModelId: Int
) : DatabaseChangeApplier {
    internal fun getProcessesForClass(schemaName: String?, className: String): List<Pair<UUID, LocalDateTime?>> {
        val classId =
            Classes.slice(Classes.id)
                .select { (Classes.schema eq schemaName) and (Classes.name eq className) and (Classes.dataModelId eq metaModelId) }
        return AutomaticEtlProcessRelations
            .join(AutomaticEtlProcesses, JoinType.INNER)
            .join(Relationships, JoinType.INNER, Relationships.id, AutomaticEtlProcessRelations.relationship)
            .join(EtlProcessesMetadata, JoinType.INNER, AutomaticEtlProcesses.id, EtlProcessesMetadata.id)
            .slice(AutomaticEtlProcessRelations.automaticEtlProcessId, EtlProcessesMetadata.lastUpdatedDate)
            .select {
                (Relationships.sourceClassId inSubQuery classId) or (Relationships.targetClassId inSubQuery classId)
            }
            .withDistinct()
            .map { it[AutomaticEtlProcessRelations.automaticEtlProcessId].value to it[EtlProcessesMetadata.lastUpdatedDate] }
    }

    private val executorsCache = HashMap<UUID, Pair<LocalDateTime?, AutomaticEtlProcessExecutor>>()

    internal fun getExecutorsForClass(schemaName: String?, className: String): List<AutomaticEtlProcessExecutor> {
        return getProcessesForClass(schemaName, className).map { (processId, lastModificationTime) ->
            executorsCache.compute(processId) { _, current ->
                // Create a new executor if:
                // 1. The current one does not exist
                // 2. The current one exists and has null LMT whereas the process has non-null LMT
                // 2. The current one exists, has non-null LMT, the process has non-null LMT and the process' LMT is more recent
                return@compute if (current === null || (lastModificationTime !== null && (current.first === null || lastModificationTime > current.first)))
                    lastModificationTime to AutomaticEtlProcessExecutor.fromDB(dataStoreDBName, processId)
                else
                    current
            }!!.second
        }
    }

    /**
     * Saves data from change events to meta model data storage.
     *
     * @param databaseChangeEvents List of database events to process.
     */
    override fun applyChange(databaseChangeEvents: List<DatabaseChangeEvent>) =
        loggedScope { logger ->
            val executors = HashMap<String, List<AutomaticEtlProcessExecutor>>()
            for (dbEvent in databaseChangeEvents) {
                transaction(DBCache.get(dataStoreDBName).database) {
                    val executorsForClass =
                        executors.computeIfAbsent(dbEvent.entityTable) {
                            getExecutorsForClass(dbEvent.entityTableSchema, dbEvent.entityTable)
                        }
                    assert(executorsForClass.mapToSet { it.logId }.size == executorsForClass.size)
                    for (executor in executorsForClass) {
                        if (executor.processEvent(dbEvent)) {
                            EtlProcessesMetadata.update({ EtlProcessesMetadata.id eq executor.logId }) {
                                it[lastExecutionTime] = Instant.now()
                            }
                        }
                    }
                }
            }
            logger.debug { "Successfully handled ${databaseChangeEvents.size} DB change events" }
        }

    companion object {
        val logger = logger()
    }
}
