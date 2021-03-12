package processm.core.models.causalnet

import org.apache.commons.collections4.multiset.HashMultiSet
import processm.core.models.commons.ProcessModelState

/**
 * State is a multi-set of pending obligations (the PM book, Definition 3.10)
 */
class CausalNetState : HashMultiSet<Dependency>, ProcessModelState {
    constructor() : super()

    constructor(stateBefore: CausalNetState) : super(stateBefore)

    internal fun execute(join: Join?, split: Split?) {
        if (join != null) {
            check(this.containsAll(join.dependencies)) { "It is impossible to execute this join in the current state" }
            for (d in join.dependencies)
                this.remove(d)
        }
        if (split != null)
            this.addAll(split.dependencies)
    }

    override fun copy(): ProcessModelState = CausalNetState(this)
}
