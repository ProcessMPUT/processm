package processm.conformance.measures.precision

import processm.conformance.measures.Measure
import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.AlignerFactory
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.PenaltyFunction
import processm.core.log.hierarchical.Log
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.URN
import processm.helpers.Trie
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * An approximate precision following Jorge Munoz-Gama, Josep Carmona: A Fresh Look at Precision in Process Conformance. BPM 2010: 211-226
 *
 * Originally, the measure is defined for PetriNets. However, the implementation here generalizes to arbitrary models
 * by using alignments.
 */
class ETCPrecision(
    val model: ProcessModel,
    val alignerFactory: AlignerFactory = AlignerFactory { m, p, _ ->
        // FIXME Composite aligner seems to cause problems with ETCPrecisionTest due to the assumptions of DecompositionAligner
        AStar(m, p)
    },
    val pool: ExecutorService = Executors.newCachedThreadPool()
) :
    Measure<Log, Double> {

    companion object {
        val URN: URN = URN("urn:processm:measures/etc_precision")

        // FIXME 100 is an arbitrary constant. Ideally, I'd forbid model-only moves altogether, but currently it's impossible
        private val penaltyFunction = PenaltyFunction(modelMove = 100)
    }

    override val URN: URN
        get() = ETCPrecision.URN

    private data class Payload(var counter: Int, val available: HashSet<Activity>)

    private fun buildTransitionSystem(alignments: Sequence<Alignment>): Trie<Activity, Payload> {
        val transitionSystem = Trie<Activity, Payload> { Payload(0, HashSet()) }
        val instance = model.createInstance()
        for (alignment in alignments) {
            var current = transitionSystem
            instance.setState(null)
            val available = ArrayList<Activity>()
            for (step in alignment.steps) {
                val a = step.modelMove ?: continue
                available.addAll(instance.availableActivities.filter { !it.isSilent })
                if (!a.isSilent) {
                    current.value.counter++
                    current.value.available.addAll(available)
                    available.clear()
                    current = current.getOrPut(a)
                }
                instance.setState(step.modelState)
            }
        }
        return transitionSystem
    }

    override fun invoke(artifact: Log): Double {
        val aligner = alignerFactory(model, penaltyFunction, pool)
        return invoke(aligner.align(artifact))
    }

    /**
     * This function is not exposed publicly as the alignments should avoid model-only moves
     */
    private fun invoke(artifact: Sequence<Alignment>): Double {
        val transitionSystem = buildTransitionSystem(artifact)
        var nom = 0
        var den = 0
        for (state in transitionSystem) {
            val n = state.trie.value.counter
            assert(n >= 0)
            if (n > 0) {
                val at = state.trie.value.available
                val rt = state.trie.children.keys
                assert(at.containsAll(rt)) { "prefix=${state.prefix} At=$at Rt=$rt" }
                val eeSize = at.count { it !in rt }
                nom += n * eeSize
                den += n * at.size
            }
        }
        return 1.0 - nom.toDouble() / den
    }
}
