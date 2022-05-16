package processm.conformance.rca.ml.spark

import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.PipelineStage
import org.apache.spark.ml.param.shared.HasInputCols
import org.apache.spark.ml.param.shared.HasOutputCols
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import processm.conformance.rca.ml.spark.SparkHelpers.isLabel
import processm.core.helpers.mapToArray
import java.util.*

/**
 * An auxiliary class to lazily construct Spark's [Pipeline], yet keep track of the schema changes eagerly
 */
class PipelineBuilder(initialSchema: StructType) {
    val pipeline: Pipeline
        get() = Pipeline().setStages(stages.toTypedArray())

    private val stages = ArrayList<PipelineStage>()
    var schema = initialSchema
        private set

    /**
     * Spark operates on principle of not removing columns from a dataset, which is not convenient from the point of view of transformation
     */
    val relevant: List<StructField>
        get() = relevantInternal
    private val relevantInternal = schema.fields().toMutableList().also { it.removeIf { it.isLabel } }

    fun add(stage: PipelineStage): PipelineBuilder {
        stages.add(stage)
        schema = stage.transformSchema(schema)
        return this
    }

    /**
     * If [fields] is not empty, set input and output columns for [stage], add it to the pipeline and update [relevant].
     * Otherwise, do nothing.
     */
    fun <T> addIfRelevant(stage: T, fields: List<StructField>)
            where T : PipelineStage, T : HasInputCols, T : HasOutputCols {
        if (fields.isEmpty())
            return
        val uuid = UUID.randomUUID().toString()
        val outputCols = fields.map { "${it.name()}_$uuid" }
        stage.set(stage.inputCols(), fields.mapToArray(StructField::name))
        stage.set(stage.outputCols(), outputCols.toTypedArray())
        add(stage)
        relevantInternal.removeAll(fields)
        relevantInternal.addAll(schema.fields().filter { it.name() in outputCols })
    }
}