package processm.core.models.processtree

import org.apache.commons.collections4.MultiSet
import org.apache.commons.collections4.multiset.HashMultiSet
import processm.core.models.commons.ProcessModelState

interface ProcessTreeState : MultiSet<Dependency>, ProcessModelState

/**
 * State is a multi-set of pending obligations (the PM book, Definition 3.10)
 */
class ProcessTreeStateImpl : HashMultiSet<Dependency>, ProcessTreeState {
    constructor() : super()

    constructor(stateBefore: ProcessTreeState) : super(stateBefore)

//    fun execute(join: Join?, split: Split?) {
//        if (join != null) {
//            check(this.containsAll(join.dependencies)) { "It is impossible to execute this join in the current state" }
//            for (d in join.dependencies)
//                this.remove(d)
//        }
//        if (split != null)
//            this.addAll(split.dependencies)
//    }
}