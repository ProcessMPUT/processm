package processm.core.models.causalnet

import org.apache.commons.collections4.MultiSet
import org.apache.commons.collections4.multiset.HashMultiSet
import processm.core.models.commons.ProcessModelState

interface CausalNetState : MultiSet<Dependency>, ProcessModelState {
}

/**
 * State is a multi-set of pending obligations (the PM book, Definition 3.10)
 */
class CausalNetStateImpl : HashMultiSet<Dependency>, CausalNetState {
    constructor() : super()

    constructor(stateBefore: CausalNetState) : super(stateBefore)

    // TODO this should be internal, but it currently interferes with using it in processm.experimental
    fun execute(join: Join?, split: Split?) {
        if (join != null) {
            check(this.containsAll(join.dependencies)) { "It is impossible to execute this join in the current state" }
            for (d in join.dependencies)
                this.remove(d)
        }
        if (split != null)
            this.addAll(split.dependencies)
    }
}