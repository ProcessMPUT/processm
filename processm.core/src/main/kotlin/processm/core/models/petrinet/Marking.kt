package processm.core.models.petrinet

import processm.core.models.commons.ProcessModelState

/**
 * The marking of a Petri net. Stores the places consisting of at least one token.
 * By contract non-positive numbers of tokens are forbidden.
 */
class Marking() : HashMap<Place, Int>(), ProcessModelState {
    companion object {
        val empty = Marking()
    }

    constructor(other: Map<Place, Int>) : this() {
        putAll(other)
    }

    constructor(vararg places: Place) : this() {
        for (place in places)
            compute(place) { _, old ->
                if (old === null) 1 else old + 1
            }
    }

    override fun copy(): Marking = Marking(this)
}

