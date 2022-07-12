package processm.etl.metamodel

import jakarta.jms.MapMessage
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultDirectedGraph
import processm.core.communication.Consumer
import processm.core.esb.Artemis
import processm.core.esb.Service
import processm.core.esb.ServiceStatus
import processm.core.helpers.toUUID
import processm.core.log.DBLogCleaner
import processm.core.log.DBXESOutputStream
import processm.core.log.Log
import processm.core.log.attribute.Attribute.Companion.CONCEPT_NAME
import processm.core.log.attribute.Attribute.Companion.IDENTITY_ID
import processm.core.log.attribute.IDAttr
import processm.core.log.attribute.StringAttr
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Converts data stored in meta model to XES log format.
 */
class MetaModelToXesConversionService : Service {
    companion object {
        private const val identityIdAttributeName = IDENTITY_ID
        private const val conceptNameAttributeName = CONCEPT_NAME
        private val logger = logger()
    }

    private val conversionsQueueLengthLimit = 10
    private val conversionWorker = MetaModelToXesConversionWorker(conversionsQueueLengthLimit)
    private val conversionThread = Thread(conversionWorker)
    private lateinit var consumer: Consumer
    override var status: ServiceStatus = ServiceStatus.Unknown
    override val dependencies: List<KClass<out Service>> = listOf(Artemis::class)

    override fun register() {
        status = ServiceStatus.Stopped
        logger.debug("$name service registered")
        consumer = Consumer()
    }

    override val name: String
        get() = "MetaModel to XES conversion"

    override fun start() {
        consumer.listen(ETL_PROCESS_CONVERSION_TOPIC, name, ::enqueueLogGeneration)
        conversionThread.start()
        status = ServiceStatus.Started
        logger.info("$name service started")
    }

    override fun stop() {
        try {
            conversionWorker.stopProcessing()
            consumer.close()
            conversionThread.interrupt()
        } finally {
            status = ServiceStatus.Stopped
            logger.info("$name service stopped")
        }
    }

    private fun enqueueLogGeneration(message: MapMessage): Boolean {
        val dataStoreId = requireNotNull(message.getString(DATA_STORE_ID)?.toUUID()) { "Missing field: $DATA_STORE_ID." }
        val etlProcessId = requireNotNull(message.getString(ETL_PROCESS_ID)?.toUUID()) { "Missing field: $ETL_PROCESS_ID." }
        val dataModelId = requireNotNull(message.getInt(DATA_MODEL_ID)) { "Missing field: $DATA_MODEL_ID." }
        val logName = requireNotNull(message.getString(ETL_PROCESS_NAME)) { "Missing field: $ETL_PROCESS_NAME." }

        return conversionWorker.enqueueConversionTask(dataStoreId, etlProcessId, dataModelId, logName)
    }

    private class MetaModelToXesConversionWorker(conversionsQueueLengthLimit: Int) : Runnable {
        private val conversionsQueue = LinkedBlockingQueue<ConversionJobDetails>(conversionsQueueLengthLimit)
        private var isStopped = false

        fun enqueueConversionTask(dataStoreId: UUID, etlProcessId: UUID, dataModelId: Int, logName: String): Boolean {
            return conversionsQueue.offer(ConversionJobDetails(dataStoreId, etlProcessId, dataModelId, logName))
        }

        fun stopProcessing() {
            isStopped = true
        }

        override fun run() {
            while (!isStopped) {
                val conversionDetails = conversionsQueue.poll(5, TimeUnit.SECONDS)

                if (conversionDetails != null) {
                    convertMetaModelToXesLog(
                        conversionDetails.dataStoreId,
                        conversionDetails.etlProcessId,
                        conversionDetails.dataModelId,
                        conversionDetails.logName)
                }
            }
        }

        private fun convertMetaModelToXesLog(dataStoreId: UUID, etlProcessId: UUID, metaModelId: Int, logName: String) {
            val dataStoreName = dataStoreId.toString()
            val xesInputStream = transaction(DBCache.get(dataStoreName).database) {
                val metaModelReader = MetaModelReader(metaModelId)
                val metaModelAppender = MetaModelAppender(metaModelReader)
                val metaModel = MetaModel(dataStoreName, metaModelReader, metaModelAppender)
                val relations: Graph<EntityID<Int>, String> = DefaultDirectedGraph(String::class.java)
                getProcessRelations(etlProcessId)
                    .forEachIndexed { i, (sourceNode, targetNode) ->
                        val sourceNodeId = metaModelReader.getClassId(sourceNode)
                        val targetNodeId = metaModelReader.getClassId(targetNode)
                        relations.addVertex(sourceNodeId)
                        relations.addVertex(targetNodeId)

                        // self-loop, not supported at the moment
                        if (sourceNodeId != targetNodeId) {
                            relations.addEdge(sourceNodeId, targetNodeId, "$i")
                        }
                    }

                val businessPerspective = DAGBusinessPerspectiveDefinition(relations)
                val traces = metaModel.buildTracesForBusinessPerspective(businessPerspective)
                return@transaction MetaModelXESInputStream(
                    businessPerspective.caseNotionClasses,
                    traces,
                    dataStoreName,
                    metaModelId
                )
            }

            DBCache.get(dataStoreName).getConnection().use { connection ->
                DBLogCleaner.removeLog(connection, etlProcessId)
                DBXESOutputStream(connection).use { dbStream ->
                    dbStream.write(xesInputStream.map {
                            val log = it as? Log ?: return@map it
                            val logAttributes = log.attributes.toMutableMap()

                            logAttributes.computeIfAbsent(identityIdAttributeName) {
                                IDAttr(
                                    identityIdAttributeName,
                                    etlProcessId
                                )
                            }
                            logAttributes.computeIfAbsent(conceptNameAttributeName) {
                                StringAttr(
                                    conceptNameAttributeName,
                                    logName
                                )
                            }

                            return@map Log(
                                logAttributes,
                                log.extensions.toMutableMap(),
                                log.traceGlobals.toMutableMap(),
                                log.eventGlobals.toMutableMap(),
                                log.traceClassifiers.toMutableMap(),
                                log.eventClassifiers.toMutableMap()
                            )
                        })
                }
            }
        }

        private fun getProcessRelations(etlProcessId: UUID): List<Pair<String, String>> {
            val sourceClassAlias = Classes.alias("c1")
            val targetClassAlias = Classes.alias("c2")
            return EtlProcessesMetadata
                .innerJoin(AutomaticEtlProcesses)
                .innerJoin(AutomaticEtlProcessRelations)
                .innerJoin(sourceClassAlias, { AutomaticEtlProcessRelations.sourceClassId }, { sourceClassAlias[Classes.id] })
                .innerJoin(targetClassAlias, { AutomaticEtlProcessRelations.targetClassId }, { targetClassAlias[Classes.id] })
                .slice(sourceClassAlias[Classes.name], targetClassAlias[Classes.name])
                .select { EtlProcessesMetadata.id eq etlProcessId }
                .map { relation -> relation[sourceClassAlias[Classes.name]] to relation[targetClassAlias[Classes.name]] }
        }

        private data class ConversionJobDetails(val dataStoreId: UUID, val etlProcessId: UUID, val dataModelId: Int, val logName: String)
    }
}
