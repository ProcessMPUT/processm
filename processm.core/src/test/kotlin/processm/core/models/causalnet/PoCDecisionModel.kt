package processm.core.models.causalnet

import processm.core.helpers.Counter
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Decision
import processm.core.models.commons.DecisionLearner
import processm.core.models.commons.DecisionModel

data class Row(val trace: Trace, val seqdecisions: Sequence<Decision>)

data class TextualExplanation(val text: String) {
    override fun toString(): String = text
}

/**
 * This is a proof-of-concept of [DecisionModel] to showcase its API
 */
class PoCDecisionModel(val featureName: String) : DecisionLearner<Row, Sequence<Row>, Sequence<TextualExplanation>>,
    DecisionModel<Row, Sequence<TextualExplanation>> {

    private class Stump<Feature, Outcome> {
        private val backend = HashMap<Feature, Counter<Outcome>>()

        fun train(f: Feature, o: Outcome) {
            backend.getOrPut(f, { Counter() }).inc(o)
        }

        fun distribution(f: Feature) = backend.getOrPut(f, { Counter() })
    }

    private val stumps = HashMap<DecisionPoint, Stump<Any?, Binding?>>()

    override fun fit(dataset: Sequence<Row>): DecisionModel<Row, Sequence<TextualExplanation>> {
        for ((trace, seqdecisions) in dataset) {
            val events = trace.events.toList()
            for ((pos, dec) in seqdecisions.withIndex()) {
                check(dec is BindingDecision)
                if (!dec.decisionPoint.isRealDecision)
                    continue
                val event = events[pos / 2]
                val feature = event.attributes[featureName]
                stumps.getOrPut(dec.decisionPoint, { Stump() }).train(feature, dec.binding)
            }
        }
        return this
    }

    override fun predict(row: Row): Sequence<TextualExplanation> =
        sequence {
            val (trace, decisions) = row
            val events = trace.events.toList()
            for ((pos, dec) in decisions.withIndex()) {
                check(dec is BindingDecision)
                val event = events[pos / 2]
                if (dec.decisionPoint.isRealDecision) {
                    val feature = event.attributes[featureName]
                    val dist =
                        stumps.getOrPut(dec.decisionPoint, { Stump<Any?, Binding?>() }).distribution(feature)
                    val sum = dist.values.sum()
                    val p = "%.3f".format(dist[dec.binding] / sum.toDouble())
                    val diststr =
                        dist.entries.joinToString(separator = " ") { (f, v) -> "$f: $v/$sum=" + "%.3f".format(v / sum.toDouble()) }
                    yield(TextualExplanation("${event.conceptName}/${dec.binding}: There was ${dist[dec.binding]}/$sum=$p probability of this decision given $featureName=$feature. The complete probability distribution is $diststr"))
                } else
                    yield(TextualExplanation("${event.conceptName}/${dec.binding}: There was no decision to make"))
            }
        }
}