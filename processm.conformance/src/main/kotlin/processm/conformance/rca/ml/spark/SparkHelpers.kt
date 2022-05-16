package processm.conformance.rca.ml.spark

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.RowFactory
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.sum
import org.apache.spark.sql.types.*
import processm.conformance.rca.Label
import processm.conformance.rca.PropositionalSparseDataset
import processm.core.Brand
import java.time.Instant

object SparkHelpers {
    private const val LABEL_METADATA = "label"

    fun PropositionalSparseDataset.toSpark(): Dataset<Row> {
        val spark = SparkSession.builder().master("local").appName(Brand.name).orCreate
        val rows = map { row ->
            val values = features.map { row[it] ?: it.default }
            RowFactory.create(*values.toTypedArray())
        }
        val fields = features.map {
            val dt = when (it.datatype) {
                Boolean::class -> DataTypes.BooleanType
                Instant::class -> DataTypes.TimestampType
                Int::class -> DataTypes.IntegerType
                Long::class -> DataTypes.LongType
                Double::class -> DataTypes.DoubleType
                String::class -> DataTypes.StringType
                else -> throw IllegalArgumentException("Unsupported data type: ${it.datatype}")
            }
            return@map StructField(it.name, dt, true, Metadata.empty())
        }
        val schema = StructType(*fields.toTypedArray())
        val df = spark.createDataFrame(rows, schema)

        val col = df.col(Label.name)
        return df.withColumn(
            Label.name,
            col.cast(DataTypes.IntegerType),
            MetadataBuilder().putBoolean(LABEL_METADATA, true).build()
        )
    }

    val StructField.isLabel
        get() = with(metadata()) { contains(LABEL_METADATA) && getBoolean(LABEL_METADATA) }

    fun Dataset<Row>.hasMissingValues(columnName: String): Boolean =
        select(sum(col(columnName).isNull.cast(DataTypes.IntegerType))).first().getAs<Long>(0) > 0
}