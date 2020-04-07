package processm.core.models.causalnet

import org.apache.commons.collections4.multiset.HashMultiSet
import processm.core.models.commons.AbstractState

/**
 * State is a multi-set of pending obligations (the PM book, Definition 3.10)
 */
class CausalNetState : HashMultiSet<Dependency>, AbstractState {
    constructor() : super()

    constructor(stateBefore: CausalNetState) : super(stateBefore)

}