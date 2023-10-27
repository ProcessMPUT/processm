package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jgrapht.Graph
import java.util.*

data class RemoteObjectID(val objectId: String, val classId: EntityID<Int>)

interface ETLProcessStub {
    val processId: UUID
    val identifyingClasses: Set<EntityID<Int>>
    val relevantClasses: Set<EntityID<Int>>

//    fun getTrace(caseIdentifier: Set<RemoteObjectID>): UUID?
//
//    fun updateTrace(traceId: UUID, newCaseIdentifier: Set<RemoteObjectID>)
//
//    fun createTrace(caseIdentifier: Set<RemoteObjectID>): UUID
//
//    fun getTracesWithObject(obj: RemoteObjectID): Sequence<UUID>
//
//    fun addObjectToTrace(traceId: UUID, obj: RemoteObjectID)
//
//    fun createAnonymousTrace(): UUID

    data class Arc(val sourceClass: EntityID<Int>, val attributeName: String, val targetClass: EntityID<Int>)

    fun getRelevanceGraph(): Graph<EntityID<Int>, Arc>
}

interface ETLProcessProvider {
    fun getProcessesForClass(className: String): List<ETLProcessStub>
//    fun getRelationsForProcess(processId: UUID): Set<Pair<EntityID<Int>, EntityID<Int>>>
//
//    fun getIdentifyingClasses(processId: UUID): Set<EntityID<Int>>


//    private val dataConnectorId: UUID = transaction(DBCache.get(dataStoreDBName).database) {
//        DataConnector.wrapRow(DataConnectors.select { DataConnectors.dataModelId eq metaModelReader.dataModelId }
//            .single()).id.value
//    }


//    private fun getProcessRelations(etlProcessId: UUID): List<Pair<EntityID<Int>, EntityID<Int>>> =
//        AutomaticEtlProcessRelations
//            .slice(AutomaticEtlProcessRelations.sourceClassId, AutomaticEtlProcessRelations.targetClassId)
//            .select {
//                AutomaticEtlProcessRelations.automaticEtlProcessId eq etlProcessId
//            }
//            .map { relation -> relation[AutomaticEtlProcessRelations.sourceClassId] to relation[AutomaticEtlProcessRelations.targetClassId] }

    //                val etlProcesses = EtlProcessesMetadata
//                    .innerJoin(AutomaticEtlProcesses)
//                    .select { EtlProcessesMetadata.dataConnectorId eq dataConnectorId and (EtlProcessesMetadata.isActive) }
//                    .map { EtlProcessMetadata.wrapRow(it) }
}