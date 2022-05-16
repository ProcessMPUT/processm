package processm.conformance.rca.ml.spark

import org.apache.spark.ml.feature.Imputer
import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.sql.types.DataTypes
import org.apache.spark.sql.types.NumericType
import org.apache.spark.sql.types.StructField
import processm.conformance.rca.PropositionalSparseDataset
import processm.conformance.rca.ml.DecisionTreeClassifier
import processm.conformance.rca.ml.spark.SparkHelpers.hasMissingValues
import processm.conformance.rca.ml.spark.SparkHelpers.isLabel
import processm.conformance.rca.ml.spark.SparkHelpers.toSpark
import processm.core.helpers.mapToArray

class SparkDecisionTreeClassifier : DecisionTreeClassifier {
    companion object {
        private const val FEATURES = "features"
    }

    /**
     * Maximal depth of the tree to be returned by the next call to [fit].
     * If <= 0, the maximal depth is unbound
     */
    var maxDepth: Int = 0

    /**
     * Encode all string columns as categorical features represented with integers (one-hot encoding for classic decision
     * trees is unnecessary), and then fit and return a decision tree for [sparseDataset]
     */
    override fun fit(sparseDataset: PropositionalSparseDataset): SparkDecisionTreeModel {
        val dataset = sparseDataset.toSpark()
        val pipelineBuilder = PipelineBuilder(dataset.schema())

        pipelineBuilder.addIfRelevant(Imputer(), pipelineBuilder.relevant.filter { it.dataType() is NumericType })

        pipelineBuilder.relevant.filter { it.dataType() == DataTypes.StringType }.let { stringFields ->
            val (withMissingValues, withoutMissingValues) = stringFields.partition { dataset.hasMissingValues(it.name()) }
            pipelineBuilder.addIfRelevant(StringIndexer().setHandleInvalid("keep"), withMissingValues)
            pipelineBuilder.addIfRelevant(StringIndexer(), withoutMissingValues)
        }

        pipelineBuilder.add(
            VectorAssembler()
                .setInputCols(pipelineBuilder.relevant.mapToArray(StructField::name))
                .setOutputCol(FEATURES)
        )

        val cls = org.apache.spark.ml.classification.DecisionTreeClassifier()
            .setFeaturesCol(FEATURES)
            .setLabelCol(pipelineBuilder.schema.fields().single { it.isLabel }.name())

        if (maxDepth > 0)
            cls.maxDepth = maxDepth

        pipelineBuilder.add(cls)
        val model = pipelineBuilder.pipeline.fit(dataset)

        return SparkDecisionTreeModel(model, model.transform(dataset).schema(), sparseDataset)
    }
}