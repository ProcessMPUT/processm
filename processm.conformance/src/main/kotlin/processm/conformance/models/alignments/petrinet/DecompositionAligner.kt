package processm.conformance.models.alignments.petrinet

import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.*
import processm.conformance.models.alignments.cache.CachingAlignerFactory
import processm.conformance.models.alignments.cache.DefaultAlignmentCache
import processm.core.helpers.*
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.models.commons.Activity
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * An aligner for [PetriNet]s that calculates alignments using the decomposition of the given net.
 * The implementation is inspired by the below work but deviates slightly.
 * Wai Lam Jonathan Lee, H.M.W. Verbeek, Jorge Munoz-Gama, Wil M.P. van der Aalst, Marcos Sepulveda, Recomposing
 * conformance: Closing the circle on decomposed alignment-based conformance checking in process mining, Information
 * Sciences 466:55-91, Elsevier, 2018. https://doi.org/10.1016/j.ins.2018.07.026
 * @property model The Petri net to align with.
 * @property penalty The penalty function.
 * @property alignerFactory The factory for base aligners. The base aligner is used to produce partial aligners for
 * the parts of the decomposed [model].
 */
class DecompositionAligner(
    override val model: PetriNet,
    override val penalty: PenaltyFunction = PenaltyFunction(),
    val alignerFactory: AlignerFactory = CachingAlignerFactory(DefaultAlignmentCache()) { m, p, _ -> AStar(m, p) },
    val pool: ExecutorService = SameThreadExecutorService
) : Aligner {

    companion object {
        private val logger = logger()
    }

    private val initialDecomposition: List<PetriNet> by lazy {
        Decomposition.createInitialDecomposition(model)
    }

    /**
     * Calculates [Alignment] for the given [trace]. Use [Thread.interrupt] to cancel calculation without yielding result.
     *
     * @throws IllegalStateException If the alignment cannot be calculated, e.g., because the final model state is not
     * reachable.
     * @throws InterruptedException If the calculation cancelled.
     */
    override fun align(trace: Trace): Alignment {
        val start = System.currentTimeMillis()
        val events = trace.events.toList()

        logger.trace { "Aligning Petri net and trace [${events.joinToString { it.conceptName ?: "" }}]" }

        val eventsWithExistingActivities =
            events.filter { e -> model.activities.any { a -> !a.isSilent && a.name == e.conceptName } }

        val alignments = decomposedAlign(eventsWithExistingActivities)
        val alignment =
            if (alignments.size == 1) alignments[0]
            else alignments.mergeDuplicateAware(eventsWithExistingActivities, penalty)

        val output =
            if (events.size == eventsWithExistingActivities.size) alignment
            else alignment.fillMissingEvents(events, penalty)

        val time = System.currentTimeMillis() - start
        logger.debug { "Calculated alignment in ${time}ms using decomposition into ${alignments.size} nets." }

        return output
    }

    private fun decomposedAlign(events: List<Event>): List<Alignment> {
        var decomposition = Decomposition.create(initialDecomposition, events)
        while (true) {
            val futures = decomposition.nets.mapIndexed { i, net ->
                pool.submit<Alignment> {
                    alignerFactory(net, penalty, pool).align(Trace(decomposition.traces[i].asSequence()))
                }
            }
            val alignments = try {
                futures.map(Future<Alignment>::get)
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            } catch (_: InterruptedException) {
                throw InterruptedException("DecompositionAligner was requested to cancel.")
            } catch (_: CancellationException) {
                throw InterruptedException("DecompositionAligner was requested to cancel.")
            } finally {
                for (future in futures)
                    future.cancel(true)
            }

            if (alignments.size == 1)
                return alignments

            val conflict = getMaxConflict(alignments, decomposition)
            if (conflict.isEmpty())
                return alignments

            decomposition = decomposition.recompose(conflict.map(decomposition.nets::get), events)
        }
    }

    /**
     * @return Indices of conflicting alignments.
     */
    private fun getMaxConflict(alignments: List<Alignment>, decomposition: Decomposition): List<Int> {
        if (alignments.size <= 1)
            return emptyList() // fast path
        var maxConflictIds = emptyList<Int>()

        for (i in alignments.indices) {
            val ids = getConflictIds(decomposition, i, alignments)
            if (ids.size > 1 /*ignore self-conflicts*/ && ids.size > maxConflictIds.size)
                maxConflictIds = ids
        }

        assert(maxConflictIds.size != 1)
        return maxConflictIds
    }

    private fun getConflictIds(decomposition: Decomposition, i: Int, alignments: List<Alignment>): List<Int> {
        val neti = decomposition.nets[i]
        val alignmenti = alignments[i]

        val conflicts = mutableListOf(i)
        for (j in i + 1 until decomposition.nets.size) {
            val netj = decomposition.nets[j].transitions
            val border = neti.transitions.filter { ti -> !ti.isSilent && netj.any { tj -> ti eq tj } }
            if (border.isEmpty())
                continue

            val alignmentj = alignments[j]
            for (activity in border) {
                val typesi = alignmenti.projectDeviationType(activity)
                val typesj = alignmentj.projectDeviationType(activity)

                // The referenced work says, the violation of any these conditions should mark a conflict:
                // 1. γ_i^aLM has an equal number of moves as γ_j^aLM.
                // 2. γ_i^aLM has the same move types as γ_j^aLM, i.e. if γ_i^aLM has one log move, then γ_j^aLM must also have one log move.
                // 3. The order of moves in γ_i^aLM and γ_j^aLM are the same.
                // This effectively refers to verifying whether typesi != typesj. However, in our tests this is not
                // enough. We require also that the border activities must not be involved in model-only moves.
                if (typesi != typesj || typesi.any { it == DeviationType.ModelDeviation }) {
                    conflicts.add(j)
                    break
                }
            }
        }

        return conflicts
    }

    private fun Alignment.projectDeviationType(activity: Activity): List<DeviationType> =
        steps.mapNotNull {
            if (it.modelMove eq activity || it.logMove?.conceptName == activity.name) it.type else null
        }

    // TODO: Introduce comparator interface/class
    private infix fun Activity?.eq(other: Activity?): Boolean =
        this === other || (this?.name == other?.name && this?.isSilent == other?.isSilent && this?.isArtificial == other?.isArtificial)

    private data class Decomposition(
        val nets: List<PetriNet>,
        val traces: List<List<Event>>
    ) {
        companion object {
            fun createInitialDecomposition(model: PetriNet): List<PetriNet> {
                assert(
                    model.transitions
                        .filterNot(Transition::isSilent)
                        .groupBy(Transition::name)
                        .none { it.value.size > 1 }
                ) { "Transition labels must be unique" }

                val nets = ArrayList<PetriNet>()
                val usedForward = HashSet<Transition>(model.transitions.size * 4 / 3, 0.75f)
                val usedBackward = HashSet<Transition>(model.transitions.size * 4 / 3, 0.75f)

                for (transition in model.transitions) {
                    if (transition.isSilent)
                        continue

                    if (transition.outPlaces.isNotEmpty() && transition !in usedForward)
                        nets.add(constructSubnet(model, transition, usedForward, usedBackward, ::collectForward))

                    if (transition.inPlaces.isNotEmpty() && transition !in usedBackward)
                        nets.add(constructSubnet(model, transition, usedForward, usedBackward, ::collectBackward))

                    if (transition.inPlaces.isEmpty() && transition.outPlaces.isEmpty())
                        nets.add(PetriNet(emptyList(), listOf(transition), Marking.empty, Marking.empty))
                }

                return nets
            }

            fun create(nets: List<PetriNet>, trace: List<Event>): Decomposition {
                val traces = nets.map { net ->
                    val transitionNames: Set<String?> = net.transitions.mapToSet(Transition::name)
                    trace.filter { e -> transitionNames.contains(e.conceptName) }
                }

                return Decomposition(nets, traces)
            }

            private fun constructSubnet(
                model: PetriNet,
                transition: Transition,
                usedForward: HashSet<Transition>,
                usedBackward: HashSet<Transition>,
                collector: (
                    model: PetriNet,
                    transition: Transition,
                    places: HashSet<Place>,
                    transitions: HashMap<Transition, Transition>,
                    usedForward: HashSet<Transition>,
                    usedBackward: HashSet<Transition>,
                ) -> Unit
            ): PetriNet {
                val places = HashSet<Place>()
                // key - transition in the source net, value - the corresponding transition in the decomposed net
                val transitions = HashMap<Transition, Transition>()

                collector(model, transition, places, transitions, usedForward, usedBackward)

                val initialMarking = model.initialMarking.filterTo(Marking()) { (p, _) -> places.contains(p) }
                val finalMarking = model.finalMarking.filterTo(Marking()) { (p, _) -> places.contains(p) }

                return PetriNet(
                    places.toList().optimize(),
                    transitions.values.toList().optimize(),
                    initialMarking = if (initialMarking.isEmpty()) Marking.empty else initialMarking,
                    finalMarking = if (finalMarking.isEmpty()) Marking.empty else finalMarking
                )
            }

            private fun collectForward(
                model: PetriNet,
                transition: Transition,
                places: HashSet<Place>,
                transitions: HashMap<Transition, Transition>,
                usedForward: HashSet<Transition>,
                usedBackward: HashSet<Transition>
            ) {
                if (!usedForward.add(transition))
                    return

                if (transition.isSilent)
                    transitions[transition] = transition
                else
                    transitions.compute(transition) { _, old ->
                        if (old === null)
                            transition.copy(inPlaces = emptyList())
                        else
                            transition
                    }

                for (place in transition.outPlaces) {
                    if (!places.add(place))
                        continue

                    collectSiblings(model, place, places, transitions, usedForward, usedBackward)
                }
            }

            private fun collectBackward(
                model: PetriNet,
                transition: Transition,
                places: HashSet<Place>,
                transitions: HashMap<Transition, Transition>,
                usedForward: HashSet<Transition>,
                usedBackward: HashSet<Transition>
            ) {
                if (!usedBackward.add(transition))
                    return

                if (transition.isSilent)
                    transitions[transition] = transition
                else
                    transitions.compute(transition) { _, old ->
                        if (old === null)
                            transition.copy(outPlaces = emptyList())
                        else
                            transition
                    }

                for (place in transition.inPlaces) {
                    if (!places.add(place))
                        continue

                    collectSiblings(model, place, places, transitions, usedForward, usedBackward)
                }
            }

            private fun collectSiblings(
                model: PetriNet,
                place: Place,
                places: HashSet<Place>,
                transitions: HashMap<Transition, Transition>,
                usedForward: HashSet<Transition>,
                usedBackward: HashSet<Transition>
            ) {
                // find the transitions following this place
                for (followingTransition in model.placeToFollowingTransition[place].orEmpty()) {
                    if (followingTransition.isSilent)
                        collectForward(model, followingTransition, places, transitions, usedForward, usedBackward)
                    collectBackward(model, followingTransition, places, transitions, usedForward, usedBackward)
                }

                // find the transitions preceding this place
                for (precedingTransition in model.placeToPrecedingTransition[place].orEmpty()) {
                    if (precedingTransition.isSilent)
                        collectBackward(model, precedingTransition, places, transitions, usedForward, usedBackward)
                    collectForward(model, precedingTransition, places, transitions, usedForward, usedBackward)
                }
            }
        }

        init {
            assert(nets.size == traces.size)
            assert(nets.isNotEmpty())
            assert(
                nets.allPairs()
                    .none { (n1, n2) -> n1.places.any(n2.places::contains) || n2.places.any(n1.places::contains) })
        }

        fun recompose(
            models: List<PetriNet>,
            trace: List<Event>
        ): Decomposition {
            assert(models.size >= 2)
            assert(nets.containsAll(models))

            val places = ArrayList<Place>()
            val transitions = ArrayList<Transition>()
            val nameToTransition = HashMap<String, Transition>()
            val initialMarking = Marking()
            val finalMarking = Marking()

            for (model in models) {
                places.addAll(model.places)
                initialMarking.putAll(model.initialMarking)
                finalMarking.putAll(model.finalMarking)

                for (originalTransition in model.transitions) {
                    if (originalTransition.isSilent)
                        transitions.add(originalTransition)
                    else
                        nameToTransition.compute(originalTransition.name) { _, existing ->
                            if (existing === null)
                                originalTransition
                            else {
                                assert(existing.inPlaces.isEmpty() || originalTransition.inPlaces.isEmpty())
                                assert(existing.outPlaces.isEmpty() || originalTransition.outPlaces.isEmpty())

                                val inPlaces = existing.inPlaces.ifEmpty(originalTransition::inPlaces)
                                val outPlaces = existing.outPlaces.ifEmpty(originalTransition::outPlaces)
                                existing.copy(inPlaces = inPlaces, outPlaces = outPlaces)
                            }
                        }
                }
            }

            transitions.addAll(nameToTransition.values)

            val newNets = ArrayList<PetriNet>(nets.size - models.size + 1)
            val newTraces = ArrayList<List<Event>>(traces.size - models.size + 1)
            for ((i, net) in nets.withIndex()) {
                if (models.contains(net))
                    continue
                newNets.add(net)
                newTraces.add(traces[i])
            }

            newNets.add(PetriNet(places, transitions, initialMarking, finalMarking))
            newTraces.add(trace.filter { e -> nameToTransition.containsKey(e.conceptName) })

            return Decomposition(newNets, newTraces)
        }
    }


}
