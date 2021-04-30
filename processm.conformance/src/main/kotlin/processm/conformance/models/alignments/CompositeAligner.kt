package processm.conformance.models.alignments

import processm.conformance.models.alignments.cache.AlignmentCache
import processm.conformance.models.alignments.cache.CachingAlignerFactory
import processm.conformance.models.alignments.cache.DefaultAlignmentCache
import processm.conformance.models.alignments.events.DefaultEventsSummarizer
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.CausalNet
import processm.core.models.commons.ProcessModel
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.converters.toPetriNet
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
 * @property cache An optional cache, to be shared by default [alignerFactories]
 * @property alignerFactories The array of aligner factories that produce individual aligners. If empty, a reasonable default is used.
 */
class CompositeAligner(
    override val model: ProcessModel,
    val penalty: PenaltyFunction = PenaltyFunction(),
    val pool: ExecutorService = Executors.newCachedThreadPool(),
    val cache: AlignmentCache? = DefaultAlignmentCache(),
    vararg alignerFactories: AlignerFactory = emptyArray()
) : Aligner {

    private val alignerFactories: List<AlignerFactory>

    companion object {
        private val ProcessTreeDecompositionAlignerFactory = AlignerFactory { model, penalty, _ ->
            ProcessTreeDecompositionAligner(model as ProcessTree, penalty)
        }
    }

    init {
        require(cache == null || alignerFactories.isEmpty()) {
            "Using cache with existing aligner factories is not supported"
        }
        this.alignerFactories = if (alignerFactories.isEmpty()) {
            val astarFactory = CachingAlignerFactory(cache) { model, penalty, _ -> AStar(model, penalty) }
            listOfNotNull(
                astarFactory,
                if (model is CausalNet) CachingAlignerFactory(cache) { model, penalty, pool ->
                    PetriDecompositionAligner(
                        (model as CausalNet).toPetriNet(),
                        penalty,
                        pool = pool,
                        alignerFactory = astarFactory
                    )
                } else null,
                if (model is CausalNet) CachingAlignerFactory(cache) { model, penalty, _ ->
                    AStar((model as CausalNet).toPetriNet(), penalty)
                } else null,
                if (model is PetriNet) CachingAlignerFactory(cache) { model, penalty, pool ->
                    PetriDecompositionAligner(
                        model as PetriNet,
                        penalty,
                        pool = pool,
                        alignerFactory = astarFactory
                    )
                } else null,
                if (model is ProcessTree) ProcessTreeDecompositionAlignerFactory else null,
            )
        } else
            alignerFactories.toList()
    }

    /**
     * Calculates [Alignment] for the given [trace].
     *
     * @return [Alignment] if computation finished before [timeout] and null otherwise.
     *
     * @throws IllegalStateException If the alignment cannot be calculated, e.g., because the final model state is not
     * reachable.
     * @throws InterruptedException If the calculation cancelled.
     */
    fun align(trace: Trace, timeout: Long, unit: TimeUnit): Alignment? {
        val completionService = ExecutorCompletionService<Alignment>(pool)
        val futures = alignerFactories.map { factory ->
            completionService.submit {
                factory(model, penalty, pool).align(trace)
            }
        }

        try {
            return if (timeout >= 0)
                completionService.poll(timeout, unit)?.get()
            else
                completionService.take().get()
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

    /**
     * Calculates [Alignment] for the given [trace]. Use [Thread.interrupt] to cancel calculation without yielding result.
     *
     * @throws IllegalStateException If the alignment cannot be calculated, e.g., because the final model state is not
     * reachable.
     * @throws InterruptedException If the calculation cancelled.
     */
    override fun align(trace: Trace): Alignment = align(trace, -1L, TimeUnit.SECONDS)!!

    /**
     * Calculates [Alignment]s for the given sequence of traces. The alignments are returned in the same order as traces in [log].
     * Uses [summarizer] to compute an alignment once for every summary returned by the summarizer.
     * Each alignment is computed for at most [timeout] [unit]s and once it is reached, the computation is interrupted and null is returned in place of the alignment.
     */
    fun align(
        log: Sequence<Trace>,
        timeout: Long = -1,
        unit: TimeUnit = TimeUnit.SECONDS,
        summarizer: EventsSummarizer<*>? = DefaultEventsSummarizer()
    ): Sequence<Alignment?> =
        summarizer?.flatMap(log) { align(it, timeout, unit) } ?: log.map { align(it, timeout, unit) }

    /**
     * Calculates [Alignment]s for the given sequence of traces. The alignments are returned in the same order as traces in [log].
     * Uses [summarizer] to compute an alignment once for every summary returned by the summarizer.
     * Each alignment is computed for at most [timeout] [unit]s and once it is reached, the computation is interrupted and null is returned in place of the alignment.
     */
    fun align(
        log: Log,
        timeout: Long = -1,
        unit: TimeUnit = TimeUnit.SECONDS,
        summarizer: EventsSummarizer<*>? = DefaultEventsSummarizer()
    ) = align(log.traces, timeout, unit, summarizer)
}
