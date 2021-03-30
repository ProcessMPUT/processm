package processm.core.models.petrinet

import processm.core.models.commons.Activity
import processm.core.models.commons.DecisionPoint
import processm.core.models.commons.ProcessModel

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
     * Calculates the collection of transitions available for firing at given [marking].
     */
    fun available(marking: Marking): Sequence<Transition> = sequence {
        for (transition in transitions) {
            if (transition.inPlaces.all { p -> p in marking })
                yield(transition)
        }
    }

    /**
     * Calculates the collection of transitions, whose execution might yield the given [marking].
     * The returned transitions might not actually run.
     */
    fun backwardAvailable(marking: Marking): Sequence<Transition> = sequence {
        for (transition in transitions)
            if (marking.keys.containsAll(transition.outPlaces))
                yield(transition)
    }
}
