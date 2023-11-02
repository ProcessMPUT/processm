package processm.etl.metamodel

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.log.AppendingDBXESOutputStream
import processm.core.logging.loggedScope
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.AutomaticEtlProcessRelations
import processm.dbmodels.models.Classes
import processm.etl.tracker.DatabaseChangeApplier
import processm.etl.tracker.DatabaseChangeApplier.DatabaseChangeEvent

class LogGeneratingDatabaseChangeApplier(
    val dataStoreDBName: String,
    val metaModelId: Int
) : DatabaseChangeApplier {

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
            for (dbEvent in databaseChangeEvents) {
                AppendingDBXESOutputStream(DBCache.get(dataStoreDBName).getConnection()).use { output ->
                    transaction(DBCache.get(dataStoreDBName).database) {
                        for (executor in getExecutorsForClass(dbEvent.entityTable)) {
                            //TODO since there's a transaction going on, we could reuse the connection here
                            val components = executor.processEvent(dbEvent).toList()
                            for (c in components)
                                println("${c::class}: ${c.attributes.toList()}")
                            output.write(components.asSequence())
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