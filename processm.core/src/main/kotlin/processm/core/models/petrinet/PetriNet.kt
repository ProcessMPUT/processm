package processm.core.models.petrinet

import processm.core.helpers.optimize
import processm.core.models.commons.Activity
import processm.core.models.commons.DecisionPoint
import processm.core.models.commons.ProcessModel
import java.util.*

/**
 * Represents the Petri net.
 * @property places the collection of places.
 * @property transitions the collection of transitions (activities).
 * @property initialMarking the initial marking, from which the process starts.
 * @property finalMarking the final marking, at which the process ends.
 */
class PetriNet(
    val places: List<Place>,
    val transitions: List<Transition>,
    val initialMarking: Marking = places.firstOrNull()?.let { Marking(it) } ?: Marking.empty,
    val finalMarking: Marking = places.lastOrNull()?.let { Marking(it) } ?: Marking.empty,
) : ProcessModel {
    override val activities: Sequence<Activity> = transitions.asSequence()

    override val startActivities: Sequence<Activity> = available(initialMarking)

    override val endActivities: Sequence<Activity> = backwardAvailable(finalMarking)

    override val decisionPoints: Sequence<DecisionPoint> = sequence {
        // FIXME: This sequence works in O(|T|^2*|P|^2). It can be reduced to O(|T|*|P|) using a mapping from places to transitions.
        val forbidden = ArrayList<Set<Transition>>()

        var res: HashSet<Transition>? = null

        for ((i, t1) in transitions.withIndex()) {
            if (t1.inPlaces.isEmpty())
                continue

            if (res === null)
                res = HashSet()
            assert(res.isEmpty())
            res.add(t1)

            for (t2 in transitions.listIterator(i + 1)) {
                if (forbidden.any { f -> t1 in f && t2 in f })
                    continue
                if (t1.inPlaces.any { p1 -> p1 in t2.inPlaces })
                    res.add(t2)
            }

            if (res.size > 1) {
                yield(DecisionPoint(res.flatMapTo(HashSet()) { t -> t.inPlaces }, res))
                forbidden.add(res)
                res = null
            } else {
                res.clear()
            }
        }
    }

    override fun createInstance(): PetriNetInstance = PetriNetInstance(this)

    /**
     * A mapping from a place to the list of following transitions.
     */
    val placeToFollowingTransition: Map<Place?, List<Transition>> =
        HashMap<Place?, List<Transition>>(places.size * 4 / 3, 0.75f).apply {
            for (transition in transitions) {
                if (transition.inPlaces.isEmpty()) {
                    (computeIfAbsent(null) { ArrayList() } as ArrayList<Transition>).add(transition)
                } else {
                    for (place in transition.inPlaces)
                        (computeIfAbsent(place) { ArrayList() } as ArrayList<Transition>).add(transition)
                }
            }
            for (entry in this)
                entry.setValue(entry.value.optimize())
        }

    /**
     * A mapping from a place to the list of preceding transitions.
     */
    val placeToPrecedingTransition: Map<Place?, List<Transition>> =
        HashMap<Place?, List<Transition>>(places.size * 4 / 3, 0.75f).apply {
            for (transition in transitions) {
                if (transition.outPlaces.isEmpty()) {
                    (computeIfAbsent(null) { ArrayList() } as ArrayList<Transition>).add(transition)
                } else {
                    for (place in transition.outPlaces)
                        (computeIfAbsent(place) { ArrayList() } as ArrayList<Transition>).add(transition)
                }
            }
            for (entry in this)
                entry.setValue(entry.value.optimize())
        }

    /**
     * Calculates the collection of transitions enabled for firing at given [marking].
     */
    fun available(marking: Marking): Sequence<Transition> = sequence {
        if (marking.isNotEmpty()) {
            val visited = IdentityHashMap<Transition, Unit>(transitions.size * 4 / 3)
            for (place in marking.keys) {
                for (transition in placeToFollowingTransition[place].orEmpty()) {
                    if (visited.put(transition, Unit) !== null)
                        continue

                    if (isAvailable(transition, marking))
                        yield(transition)
                }
            }
        }

        yieldAll(placeToFollowingTransition[null].orEmpty())
    }

    /**
     * Verifies whether [transition] is enabled in the given [marking].
     */
    fun isAvailable(transition: Transition, marking: Marking): Boolean {
        val inPlaces = transition.inPlaces
        return inPlaces.size <= 1 || (inPlaces.size <= marking.size && inPlaces.all(marking::containsKey))
    }

    /**
     * Calculates the collection of transitions, whose execution might yield the given [marking].
     * The returned transitions might not actually run.
     */
    fun backwardAvailable(marking: Marking): Sequence<Transition> = sequence {
        if (marking.isNotEmpty()) {
            val markingKeys = marking.keys
            val markingSize = markingKeys.size
            val visited = IdentityHashMap<Transition, Unit>(transitions.size * 4 / 3)
            for (place in markingKeys) {
                for (transition in placeToPrecedingTransition[place].orEmpty()) {
                    if (visited.put(transition, Unit) !== null)
                        continue

                    val outPlaces = transition.outPlaces
                    if (outPlaces.size <= markingSize && markingKeys.containsAll(outPlaces))
                        yield(transition)
                }
            }
        }

        yieldAll(placeToPrecedingTransition[null].orEmpty())
    }

    /**
     * Produces a copy of this [PetriNet] stripped out of transitions that must not run and places without succeeding
     * transitions except the places in the [finalMarking].
     */
    fun dropDeadParts(): PetriNet {
        // drop transitions that must not run because there is no way to put token in its input place(s)
        val usedTransitions = transitions.filter { t ->
            t.inPlaces.all { p -> p in initialMarking || transitions.any { t2 -> p in t2.outPlaces } }
        }

        // drop places that do not have outgoing arc to a transition except the places in the end marking
        val usedPlaces = places.filter { p ->
            p in initialMarking || p in finalMarking || usedTransitions.any { t -> p in t.inPlaces }
        }

        assert(initialMarking.keys.all { p -> p in usedPlaces })
        assert(finalMarking.keys.all { p -> p in usedPlaces })

        return PetriNet(usedPlaces, usedTransitions, initialMarking, finalMarking)
    }
}
