package processm.conformance.models.alignments

import processm.core.log.hierarchical.Trace
import processm.core.models.commons.ProcessModel
import processm.core.models.petrinet.PetriNet
import processm.core.models.processtree.ProcessTree
import java.util.concurrent.*
import processm.conformance.models.alignments.petrinet.DecompositionAligner as PetriDecompositionAligner
import processm.conformance.models.alignments.processtree.DecompositionAligner as ProcessTreeDecompositionAligner

/**
 * An ensemble of [Aligner]s composed using the aligners produced by the provided [alignerFactories].
 * This class aligns the [model] to a trace in parallel using one aligner produced by every aligner factory and returns
 * the first-found [Alignment], discarding the remaining results.
 *
 * @property model The model to align with.
 * @property penalty The penalty function.
 * @property pool The thread pool to run the aligner in.
 * @property alignerFactories The array of aligner factories that produce individual aligners. Must contain at least
 * two [Aligner]s.
 */
class CompositeAligner(
    val model: ProcessModel,
    val penalty: PenaltyFunction = PenaltyFunction(),
    val pool: ExecutorService = Executors.newCachedThreadPool(),
    vararg val alignerFactories: AlignerFactory =
        listOfNotNull(
            AStarAlignerFactory,
            if (model is PetriNet) PetriDecompositionAlignerFactory else null,
            if (model is ProcessTree) ProcessTreeDecompositionAlignerFactory else null,
        ).toTypedArray()
) : Aligner {

    companion object {
        /**
         * Timeout in microseconds for waiting for every single Future.
         */
        private const val TIMEOUT = 100L
        private val AStarAlignerFactory = AlignerFactory { model, penalty, _ -> AStar(model, penalty) }

        private val PetriDecompositionAlignerFactory = AlignerFactory { model, penalty, pool ->
            PetriDecompositionAligner(model as PetriNet, penalty, pool = pool)
        }

        private val ProcessTreeDecompositionAlignerFactory = AlignerFactory { model, penalty, _ ->
            ProcessTreeDecompositionAligner(model as ProcessTree, penalty)
        }
    }

    init {
        require(alignerFactories.size >= 2) {
            "Provide at least 2 aligner factories."
        }
    }

    /**
     * Calculates [Alignment] for the given [trace]. Use [Thread.interrupt] to cancel calculation without yielding result.
     *
     * @throws IllegalStateException If the alignment cannot be calculated, e.g., because the final model state is not
     * reachable.
     * @throws InterruptedException If the calculation cancelled.
     */
    override fun align(trace: Trace): Alignment {
        val futures = alignerFactories.map { factory ->
            pool.submit<Alignment> {
                factory(model, penalty, pool).align(trace)
            }
        }

        try {
            while (true) {
                for (i in futures.indices) {
                    val future = futures[i]
                    try {
                        return future.get(TIMEOUT, TimeUnit.MICROSECONDS)
                    } catch (_: TimeoutException) {
                        // ignore
                    }
                }
            }
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } catch (_: InterruptedException) {
            throw InterruptedException("CompositeAligner was requested to cancel.")
        } catch (_: CancellationException) {
            throw InterruptedException("CompositeAligner was requested to cancel.")
        } finally {
            for (future in futures)
                future.cancel(true)
        }
    }
}
