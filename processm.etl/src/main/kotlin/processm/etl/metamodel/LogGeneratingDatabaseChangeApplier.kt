package processm.etl.metamodel

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import processm.core.logging.loggedScope
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.AutomaticEtlProcessRelations
import processm.dbmodels.models.Classes
import processm.dbmodels.models.EtlProcessesMetadata
import processm.etl.tracker.DatabaseChangeApplier
import processm.etl.tracker.DatabaseChangeApplier.DatabaseChangeEvent
import java.time.Instant

class LogGeneratingDatabaseChangeApplier(
    val dataStoreDBName: String,
    val metaModelId: Int
) : DatabaseChangeApplier {

    //TODO split into retrieving process IDs and into creating executors, as the same executor will be reused by different classes
    internal fun getExecutorsForClass(className: String): List<AutomaticEtlProcessExecutor> {
        val classId =
            Classes.slice(Classes.id).select { (Classes.name eq className) and (Classes.dataModelId eq metaModelId) }
        return AutomaticEtlProcessRelations
            .slice(AutomaticEtlProcessRelations.automaticEtlProcessId)
            .select {
                (AutomaticEtlProcessRelations.sourceClassId inSubQuery classId) or (AutomaticEtlProcessRelations.targetClassId inSubQuery classId)
            }
            .distinct()
            .map {
                AutomaticEtlProcessExecutor.fromDB(
                    dataStoreDBName,
                    it[AutomaticEtlProcessRelations.automaticEtlProcessId].value
                )
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
                        executors.computeIfAbsent(dbEvent.entityTable) { getExecutorsForClass(dbEvent.entityTable) }
                    for (executor in executorsForClass) {
                        if (executor.processEvent(dbEvent)) {
                            //TODO ugly!!
                            EtlProcessesMetadata.update({ EtlProcessesMetadata.id eq executor.logId }) {
                                it[lastExecutionTime] = Instant.now()
                            }
                        }
                    }
                }
            }
            logger.debug("Successfully handled ${databaseChangeEvents.count()} DB change events")
        }

    companion object {
        val logger = logger()
    }
}