package processm.enhancement.kpi

import jakarta.jms.MapMessage
import jakarta.jms.Message
import org.quartz.*
import processm.core.communication.Producer
import processm.core.esb.AbstractJobService
import processm.core.esb.ServiceJob
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.commons.ProcessModel
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.models.ModelTypeDto
import processm.helpers.toUUID
import processm.logging.loggedScope
import java.util.*
import processm.core.models.causalnet.DBSerializer as CausalNetDBSerializer
import processm.core.models.petrinet.DBSerializer as PetriNetDBSerializer

class AlignerKPIService : AbstractJobService(
    QUARTZ_CONFIG,
    TOPIC,
    "$TYPE = '$TYPE_REQUEST'"
) {
    companion object {
        /**
         * The JMS topic that [AlignerKPIService] listens to and sends responeses to.
         */
        const val TOPIC = "aligner_kpi"

        /**
         * The datastore id message property of type [String].
         */
        const val DATASTORE_ID = "datastore_id"

        /**
         * The model type message property of type [ModelTypeDto] encoded using [String].
         */
        const val MODEL_TYPE = "model_type"

        /**
         * The model id message property of type [String].
         */
        const val MODEL_ID = "model_id"

        /**
         * The PQL query to fetch log to align model with.
         */
        const val QUERY = "query"

        /**
         * The type of the message property.
         */
        const val TYPE = "type"

        /**
         * The value corresponding to request message for [TYPE].
         */
        const val TYPE_REQUEST = "request"

        /**
         * The value corresponding to response message for [TYPE].
         */
        const val TYPE_REPORT = "report"

        /**
         * The property containing the KPI report.
         */
        const val REPORT = "report"

        /**
         * The property of error message (if any).
         */
        const val ERROR = "error"

        private const val QUARTZ_CONFIG = "quartz-alignerkpi.properties"


        private val producer = Producer()
    }

    override val name: String
        get() = "Aligner KPI"

    override fun loadJobs(): List<Pair<JobDetail, Trigger>> {
        return emptyList()
    }

    override fun messageToJobs(message: Message): List<Pair<JobDetail, Trigger>> {
        require(message is MapMessage) { "Unrecognized message $message." }

        val type = message.getStringProperty(TYPE)
        require(type == TYPE_REQUEST) { "Received other message type than $TYPE_REQUEST: $type" }

        val datastoreId = UUID.fromString(message.getString(DATASTORE_ID))
        val modelTypeStr = message.getString(MODEL_TYPE)
        val modelType = ModelTypeDto.valueOf(modelTypeStr)
        val modelId = message.getString(MODEL_ID)
        val query = message.getString(QUERY)

        return listOf(createJob(datastoreId, modelType, modelId, query))
    }

    private fun createJob(
        dataStore: UUID,
        modelType: ModelTypeDto,
        modelId: String,
        query: String
    ): Pair<JobDetail, Trigger> =
        loggedScope {
            val job = JobBuilder
                .newJob(AlignerKPIJob::class.java)
                .usingJobData(DATASTORE_ID, dataStore.toString())
                .usingJobData(MODEL_TYPE, modelType.toString())
                .usingJobData(MODEL_ID, modelId)
                .usingJobData(QUERY, query)
                .build()
            val trigger = TriggerBuilder
                .newTrigger()
                .startNow()
                .build()

            return job to trigger
        }


    class AlignerKPIJob : ServiceJob {
        override fun execute(context: JobExecutionContext?) = loggedScope { logger ->
            val ctx = requireNotNull(context)
            val dataStoreId = (ctx.mergedJobDataMap[DATASTORE_ID] as String).toUUID()
            val modelType = ModelTypeDto.valueOf(ctx.mergedJobDataMap[MODEL_TYPE] as String)
            val modelId = ctx.mergedJobDataMap[MODEL_ID] as String
            val query = ctx.mergedJobDataMap[QUERY] as String

            producer.produce(TOPIC) {
                try {
                    logger.debug("Calculating alignment-based KPI for datastore $dataStoreId, $modelType $modelId, query $query")

                    setStringProperty(TYPE, TYPE_REPORT)
                    setString(DATASTORE_ID, dataStoreId.toString())
                    setString(MODEL_TYPE, modelType.toString())
                    setString(MODEL_ID, modelId)
                    setString(QUERY, query)

                    val model = getModel(modelType, dataStoreId, modelId)
                    val calculator = Calculator(model)
                    val log = DBHierarchicalXESInputStream(
                        dataStoreId.toString(),
                        Query(query),
                        false
                    )
                    val report = calculator.calculate(log)
                    setString(REPORT, report.toJson())

                } catch (exception: Exception) {
                    logger.error(
                        "Error calculating alignment-based KPI for datastore $dataStoreId, $modelType $modelId, query $query",
                        exception
                    )
                    setString(ERROR, exception.message)
                }
            }

        }

        private fun getModel(
            modelType: ModelTypeDto?,
            dataStoreId: UUID?,
            modelId: String
        ): ProcessModel = when (modelType) {
            ModelTypeDto.CausalNet -> CausalNetDBSerializer.fetch(
                DBCache.get(dataStoreId.toString()).database,
                modelId.toInt()
            )

            ModelTypeDto.PetriNet -> PetriNetDBSerializer.fetch(
                DBCache.get(dataStoreId.toString()).database,
                requireNotNull(modelId.toUUID())
            )

            else -> TODO("Retrieval of model type $modelType is not implemented.")
        }
    }
}
