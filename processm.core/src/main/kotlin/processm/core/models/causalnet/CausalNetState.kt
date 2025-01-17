package processm.core.models.causalnet

import processm.core.models.commons.ProcessModelState
import processm.helpers.HashMultiSet
import processm.helpers.MutableMultiSet

interface CausalNetState : MutableMultiSet<Dependency>, ProcessModelState {
    val isFresh: Boolean
}

/**
 * State is a multi-set of pending obligations (the PM book, Definition 3.10)
 *
 * Sources in rows, targets in columns, and numbers of occurrences in values.
 */
open class CausalNetStateImpl : HashMultiSet<Dependency>, CausalNetState {
    constructor() : super()

    constructor(stateBefore: CausalNetState) : super(stateBefore)

    override var isFresh: Boolean = true
        protected set

    // TODO this should be internal, but it currently interferes with using it in processm.experimental
    open fun execute(join: Join?, split: Split?) {
        isFresh = false
        if (join !== null) {
            check(this.containsAll(join.dependencies)) { "It is impossible to execute this join in the current state" }
            for (d in join.dependencies)
                this.remove(d, 1)
        }
        if (split !== null)
            this.addAll(split.dependencies)
    }

    override fun clear() {
        super.clear()
        isFresh = true
    }

    override fun hashCode(): Int =
        isFresh.hashCode() xor super.hashCode()

    override fun equals(other: Any?): Boolean =
        other is CausalNetStateImpl && isFresh == other.isFresh && super.equals(other)

    override fun copy(): ProcessModelState = CausalNetStateImpl(this).also { it.isFresh = this.isFresh }
}
