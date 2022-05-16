package processm.conformance.rca.ml.spark

import org.apache.spark.ml.PipelineModel
import org.apache.spark.ml.attribute.Attribute
import org.apache.spark.ml.attribute.AttributeGroup
import org.apache.spark.ml.attribute.NominalAttribute
import org.apache.spark.ml.classification.DecisionTreeClassificationModel
import org.apache.spark.ml.feature.ImputerModel
import org.apache.spark.ml.feature.StringIndexerModel
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.param.shared.HasInputCols
import org.apache.spark.ml.param.shared.HasOutputCols
import org.apache.spark.ml.tree.CategoricalSplit
import org.apache.spark.ml.tree.ContinuousSplit
import org.apache.spark.ml.tree.LeafNode
import org.apache.spark.sql.types.StructType
import processm.conformance.rca.Feature
import processm.conformance.rca.PropositionalSparseDataset
import processm.conformance.rca.ml.DecisionTreeModel
import processm.core.logging.debug
import processm.core.logging.logger
import kotlin.math.absoluteValue

/**
 * A [DecisionTreeModel] wrapping a decision tree from Spark.
 * Not to be created manually, only by using [SparkDecisionTreeClassifier]
 */
class SparkDecisionTreeModel internal constructor(
    private val model: PipelineModel,
    private val schema: StructType,
    private val originalDataset: PropositionalSparseDataset
) : DecisionTreeModel {

    companion object {
        private val logger = logger()
    }

    override val depth: Int
        get() = (model.stages().last() as DecisionTreeClassificationModel).rootNode().subtreeDepth()

    override val root = visitNode((model.stages().last() as DecisionTreeClassificationModel).rootNode())

    private interface GenericSplit {
        val feature: Any
    }

    private data class GenericCategoricalSplit(override val feature: Any, val left: List<Any?>, val right: List<Any?>) :
        GenericSplit

    private data class GenericContinuousSplit(override val feature: Any, val threshold: Double) : GenericSplit

    private fun featureByName(name: String): Feature {
        logger.debug { "featureByName($name) ${originalDataset.features.map { it.name }}" }
        return originalDataset.features.single { it.name == name }
    }

    private fun translate(idx: Int, stage: VectorAssembler): String =
        AttributeGroup.fromStructField(schema.apply(stage.outputCol)).attributes().get()
            .single { it.index().get() == idx }.name().get()

    private fun translate(split: GenericCategoricalSplit, stage: VectorAssembler) =
        GenericCategoricalSplit(translate(split.feature as Int, stage), split.left, split.right)


    private fun translate(split: GenericContinuousSplit, stage: VectorAssembler) =
        GenericContinuousSplit(translate(split.feature as Int, stage), split.threshold)

    private fun <T> revertColumn(name: Any, stage: T): String? where T : HasInputCols, T : HasOutputCols {
        require(name is String)
        if (stage.inputCols.isEmpty())
            return name
        require(stage.inputCols.size == stage.outputCols.size)
        val idx = stage.outputCols.indexOf(name)
        return if (idx >= 0) stage.inputCols[idx] else null
    }

    private fun translate(split: GenericContinuousSplit, stage: ImputerModel): GenericContinuousSplit {
        val inp = revertColumn(split.feature, stage) ?: return split
        return GenericContinuousSplit(inp, split.threshold)
    }

    private fun translate(split: GenericCategoricalSplit, stage: StringIndexerModel): GenericCategoricalSplit {
        fun NominalAttribute.decode(idx: Int): String? {
            val v = getValue(idx)
            return if (v == "__unknown") // This seems to be a hardcoded string in StringIndexer. See https://github.com/apache/spark/blob/17b85ff97569a43d7fd33863d17bfdaf62d539e0/mllib/src/main/scala/org/apache/spark/ml/feature/StringIndexer.scala#L438
                null
            else
                v
        }

        val name = split.feature as String
        val inName = revertColumn(name, stage) ?: return split
        val attr = Attribute.fromStructField(schema.apply(name))
        require(attr is NominalAttribute)
        val left = split.left as List<Double>
        val right = split.right as List<Double>
        return GenericCategoricalSplit(
            inName,
            left.map { attr.decode(it.toInt()) },
            right.map { attr.decode(it.toInt()) })
    }

    private fun translate(split: CategoricalSplit): DecisionTreeModel.CategoricalSplit<Any> {
        var split = GenericCategoricalSplit(
            split.featureIndex(),
            split.leftCategories().toList(),
            split.rightCategories().toList()
        )
        for (stage in model.stages().reversed()) {
            split = when (stage) {
                is DecisionTreeClassificationModel -> continue
                is VectorAssembler -> translate(split, stage)
                is StringIndexerModel -> translate(split, stage)
                is ImputerModel -> continue // TODO?
                else -> TODO("${stage::class} not implemented")
            }
        }
        val f = split.feature
        check(f is String)
        return DecisionTreeModel.CategoricalSplit(featureByName(f), split.left.toSet(), split.right.toSet())
    }

    private fun translate(split: ContinuousSplit): DecisionTreeModel.Split {
        var split = GenericContinuousSplit(split.featureIndex(), split.threshold())
        for (stage in model.stages().reversed()) {
            split = when (stage) {
                is DecisionTreeClassificationModel -> continue
                is VectorAssembler -> translate(split, stage)
                is StringIndexerModel -> continue
                is ImputerModel -> translate(split, stage)
                else -> TODO("${stage::class} not implemented")
            }
        }
        val fname = split.feature
        check(fname is String)
        val f = featureByName(fname)
        return if (f.datatype == Boolean::class) {
            assert((split.threshold - 0.5).absoluteValue < 0.001)
            DecisionTreeModel.CategoricalSplit(f, setOf(false), setOf(true))
        } else {
            DecisionTreeModel.ContinuousSplit(f, split.threshold)
        }
    }

    private fun visitNode(node: org.apache.spark.ml.tree.Node): DecisionTreeModel.Node {
        return if (node is org.apache.spark.ml.tree.InternalNode) {
            val split = node.split()
            val translatedSplit = if (split is CategoricalSplit) {
                translate(split)
            } else {
                check(split is ContinuousSplit)
                translate(split)
            }
            DecisionTreeModel.InternalNode(translatedSplit, visitNode(node.leftChild()), visitNode(node.rightChild()))
        } else {
            check(node is LeafNode)
            DecisionTreeModel.Leaf(node.prediction() >= 0.5, node.impurity())
        }
    }

}