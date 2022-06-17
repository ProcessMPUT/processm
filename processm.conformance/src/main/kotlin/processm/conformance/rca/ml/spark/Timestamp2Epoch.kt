package processm.conformance.rca.ml.spark

import org.apache.spark.ml.Transformer
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.ml.param.StringArrayParam
import org.apache.spark.ml.param.shared.HasInputCols
import org.apache.spark.ml.param.shared.HasOutputCols
import org.apache.spark.ml.util.Identifiable
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.DataTypes
import org.apache.spark.sql.types.StructType
import scala.collection.JavaConverters.collectionAsScalaIterable

/**
 * Casts columns of the type [DataTypes.TimestampType] to [DataTypes.LongType]
 */
class Timestamp2Epoch(private val uid: String = Identifiable.randomUID(Timestamp2Epoch::class.simpleName)) :
    Transformer(), HasInputCols, HasOutputCols {

    private val inputCols = StringArrayParam(this, "inputCols", "")
    private val outputCols = StringArrayParam(this, "outputCols", "")

    override fun uid(): String = uid

    override fun copy(extra: ParamMap?): Timestamp2Epoch {
        throw NotImplementedError("This function is currently not needed")
    }

    override fun `org$apache$spark$ml$param$shared$HasOutputCols$_setter_$outputCols_$eq`(`x$1`: StringArrayParam?) {
        throw NotImplementedError("It is not clear what this function is supposed to do.")
    }

    override fun outputCols(): StringArrayParam = outputCols

    override fun `org$apache$spark$ml$param$shared$HasInputCols$_setter_$inputCols_$eq`(`x$1`: StringArrayParam?) {
        throw NotImplementedError("It is not clear what this function is supposed to do.")
    }

    override fun inputCols(): StringArrayParam = inputCols

    override fun transformSchema(schema: StructType): StructType {
        var result = schema
        for ((inp, out) in get(inputCols).get() zip get(outputCols).get())
            result = result.add(out, DataTypes.LongType, result.find { it.name() == inp }.get().nullable())
        return result
    }

    override fun transform(dataset: Dataset<*>): Dataset<Row> {
        val outNames = get(outputCols).get()
        val columns = (get(inputCols).get() zip outNames).map { (inp, out) ->
            dataset.col(inp).cast(DataTypes.LongType).`as`(out)
        }
        return dataset.withColumns(
            collectionAsScalaIterable(outNames.toList()).toSeq(),
            collectionAsScalaIterable(columns).toSeq()
        )
    }
}