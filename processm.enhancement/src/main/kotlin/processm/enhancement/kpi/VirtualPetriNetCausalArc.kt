package processm.enhancement.kpi

import kotlinx.serialization.Serializable
import processm.core.models.commons.CausalArc
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition

/**
 * This class represents two arcs in a PetriNet: an arc from the transition [source] to the place [via], and an arc from
 * the place to the transition [target]. It is required that [via] is in [Transition.outPlaces] for [source], and in
 * [Transition.inPlaces] for [target].
 *
 * Considering two arcs jointly enables computing more useful distributions in [Calculator]
 */
@Serializable
data class VirtualPetriNetCausalArc(override val source: Transition, override val target: Transition, val via: Place) : CausalArc {
    init {
        require(via in source.outPlaces)
        require(via in target.inPlaces)
    }

    override fun toString(): String = "[$source -> ... -> $target]"
}