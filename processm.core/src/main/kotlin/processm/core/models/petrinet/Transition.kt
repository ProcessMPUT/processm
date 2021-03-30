package processm.core.models.petrinet

import processm.core.models.commons.Activity

/**
 * A transition in a Petri net.
 *
 * @property inPlaces The collection of the preceding places.
 * @property outPlaces The collection of the succeeding places.
 */
data class Transition(
    override val name: String,
    val inPlaces: Collection<Place> = emptyList(),
    val outPlaces: Collection<Place> = emptyList(),
    override val isSilent: Boolean = false
) : Activity
