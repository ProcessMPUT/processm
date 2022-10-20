package processm.conformance.rca

import processm.conformance.models.alignments.Alignment
import processm.conformance.rca.ml.DecisionTreeModel
import processm.conformance.rca.ml.spark.SparkDecisionTreeClassifier
import java.util.*


data class RootCauseAnalysisDataSet(val positive: List<Alignment>, val negative: List<Alignment>)

/**
 * A sparse propositional dataset stored as a list of maps.
 */
class PropositionalSparseDataset(base: List<Map<Feature, Any>>) : List<Map<Feature, Any>> by base {

    /**
     * The set of features used by the dataset in an arbitrary, but constant order
     */
    val features: List<Feature> by lazy {
        return@lazy flatMapTo(HashSet()) { it.keys }.toList() //toList to fix the order, whatever it is
    }
}

/**
 * Generates propositional features from the given [Alignment]. Currently three types of features are generated for every attribute of every event:
 *
 * 1. [EventFeature] with the value of the attribute
 * 2. [AnyNamedEventFeature] with the value `true`
 * 3. [AnyEventFeature] with the value `true`
 *
 * [List]s are ignored as there's no obvious way to incorporate them and [UUID]s are ignored on purpose as useless
 */
fun Alignment.propositionalize(): HashMap<Feature, Any> {
    val ctr = HashMap<String?, Int>()
    val result = HashMap<Feature, Any>()
    for (step in steps) {
        val e = step.logMove
        if (e !== null) {
            val occurrences = ctr.compute(e.conceptName) { _, v ->
                return@compute (v ?: 0) + 1
            }!!
            for ((k, v) in e.attributes) {
                if (v is UUID)
                    continue    //IDs are not useful in machine learning
                if (v is List<*>)
                    continue //TODO Currently I see no clear advantage in coming up with a way to support ListAttrs and this is not exactly obvious
                v?.let {
                    result[EventFeature(e.conceptName, occurrences, k, v::class)] = it
                    result[AnyNamedEventFeature(e.conceptName, k, it)] = true
                    result[AnyEventFeature(k, it)] = true
                }
            }
        }
    }
    return result
}

internal fun List<Alignment>.propositionalize(label: Boolean): PropositionalSparseDataset =
    PropositionalSparseDataset(map { alignment ->
        val av = alignment.propositionalize()
        av[Label] = label
        return@map av
    })

internal fun RootCauseAnalysisDataSet.propositionalize(): PropositionalSparseDataset =
    PropositionalSparseDataset(positive.propositionalize(true) + negative.propositionalize(false))

/**
 * Returns a sequence of [DecisionTreeModel]s from the most complex (most deepest) to the simplest (shallowest) differentiating
 * between [RootCauseAnalysisDataSet.positive] and [RootCauseAnalysisDataSet.negative] examples
 */
fun RootCauseAnalysisDataSet.explain(): Sequence<DecisionTreeModel> = sequence {
    val propositional = propositionalize()
    val cls = SparkDecisionTreeClassifier()
    while (true) {
        val model = cls.fit(propositional)
        yield(model)
        if (model.depth > 1)
            cls.maxDepth = model.depth - 1
        else
            break
    }
}
