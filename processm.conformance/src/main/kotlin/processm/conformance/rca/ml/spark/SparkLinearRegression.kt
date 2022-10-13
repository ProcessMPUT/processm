package processm.conformance.rca.ml.spark

import org.apache.spark.ml.PipelineModel
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.regression.LinearRegression
import org.apache.spark.ml.regression.LinearRegressionModel
import org.apache.spark.sql.Row
import org.apache.spark.sql.RowFactory
import org.apache.spark.sql.types.DataTypes
import org.apache.spark.sql.types.Metadata
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import processm.conformance.conceptdrift.numerical.isEffectivelyZero
import processm.conformance.rca.ml.spark.SparkHelpers.getSparkSession
import processm.core.models.commons.DecisionLearner

class SparkLinearRegression : DecisionLearner<List<Double>, List<Pair<List<Double>, Double>>, Double> {

    private val spark = getSparkSession()
    private lateinit var model: PipelineModel

    var fitIntercept: Boolean = true

    override fun fit(dataset: List<Pair<List<Double>, Double>>): processm.conformance.rca.ml.LinearRegressionModel {
        require(dataset.isNotEmpty())

        val nFeatures = dataset[0].first.size
        val features = (0 until nFeatures).map { "x-$it" }
        val columns = arrayListOf(StructField("y", DataTypes.DoubleType, false, Metadata.empty()))
        features.mapTo(columns) { StructField(it, DataTypes.DoubleType, false, Metadata.empty()) }

        val rows = ArrayList<Row>()
        for ((x, y) in dataset) {
            require(x.size == nFeatures)
            val row = arrayListOf(y)
            row.addAll(x)
            rows.add(RowFactory.create(*row.toTypedArray()))
        }

        val df = spark.createDataFrame(rows, StructType(columns.toTypedArray()))

        val pipelineBuilder = PipelineBuilder(df.schema())

        pipelineBuilder.add(
            VectorAssembler()
                .setInputCols(features.toTypedArray())
                .setOutputCol("features")
        )

        pipelineBuilder.add(
            LinearRegression()
                .setFeaturesCol("features")
                .setLabelCol("y")
                .setFitIntercept(fitIntercept)
        )

        model = pipelineBuilder.pipeline.fit(df)
        val lrModel = model.stages().last() as LinearRegressionModel
        assert(fitIntercept || lrModel.intercept().isEffectivelyZero())
        return processm.conformance.rca.ml.LinearRegressionModel(
            lrModel.coefficients().toArray().toList(),
            lrModel.intercept()
        )
    }
}