package processm.core.models.causalnet

import processm.core.helpers.Counter
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.AbstractDecision
import processm.core.models.commons.AbstractDecisionModel
import processm.core.models.commons.AbstractExplanation

/**
 * This is a proof-of-concept of [AbstractDecisionModel] to showcase its API
 */
class PoCDecisionModel(val featureName: String) : AbstractDecisionModel {

    private class Stump<Feature, Outcome> {
        private val backend = HashMap<Feature, Counter<Outcome>>()

        fun train(f: Feature, o: Outcome) {
            backend.getOrPut(f, { Counter() }).inc(o)
        }

        fun distribution(f: Feature) = backend.getOrPut(f, { Counter() })
    }

    private data class TextualExplanation(val text: String) : AbstractExplanation {
        override fun toString(): String = text
    }

    private val stumps = HashMap<DecisionPoint, Stump<Any?, Binding?>>()

    override fun train(trace: Trace, seqdecisions: Sequence<AbstractDecision>) {
        val events = trace.events.toList()
        for ((pos, dec) in seqdecisions.withIndex()) {
            check(dec is BindingDecision)
            if (!dec.decisionPoint.isStrict)
                continue
            val event = events[pos / 2]
            val feature = event.attributes[featureName]?.getValue()
            stumps.getOrPut(dec.decisionPoint, { Stump() }).train(feature, dec.binding)
        }
    }

    override fun explain(trace: Trace, decisions: Sequence<AbstractDecision>): Sequence<AbstractExplanation> =
        sequence {
            val events = trace.events.toList()
            for ((pos, dec) in decisions.withIndex()) {
                check(dec is BindingDecision)
                val event = events[pos / 2]
                if (dec.decisionPoint.isStrict) {
                    val feature = event.attributes[featureName]?.getValue()
                    val dist =
                        stumps.getOrPut(dec.decisionPoint, { Stump<Any?,Binding?>() }).distribution(feature)
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