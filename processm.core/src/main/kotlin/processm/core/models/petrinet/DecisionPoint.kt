package processm.core.models.petrinet

import processm.core.models.commons.DecisionPoint

/**
 * The decision point in the Petri net: the set of [places], whose state determine available [transitions].
 */
data class DecisionPoint(
    val places: Set<Place>,
    val transitions: Set<Transition>
) : DecisionPoint {

    init {
        assert(places.isNotEmpty())
        assert(transitions.size >= 2)
    }

    /**
     * Indicates whether this decision point is free-choice, i.e., independent of the rest of the Petri net.
     * The free-choice decision points make decisions locally. The non-free-choice decision points depend on
     * the availability of tokens that come from the decisions made previously in the net.
     */
    val isFreeChoice: Boolean = transitions.all { t -> t.inPlaces == transitions.first().inPlaces }

    override val possibleOutcomes: Collection<TransitionDecision> =
        transitions.map { TransitionDecision(it, this) }

}
