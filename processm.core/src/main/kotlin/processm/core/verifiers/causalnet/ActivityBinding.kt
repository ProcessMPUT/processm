package processm.core.verifiers.causalnet

import processm.core.models.causalnet.CausalNetState
import processm.core.models.causalnet.CausalNetStateImpl
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node

/**
 * Computes cartesian product
 */
internal infix fun <A, B> Collection<A>.times(right: Collection<B>): List<Pair<A, B>> {
    return this.flatMap { a -> right.map { b -> a to b } }
}

/**
 * Denotes the occurrence of activity [a] with input binding [i] and output binding [o]
 *
 * Follows the description from the PM book, right above Definition 3.9
 */
class ActivityBinding(
    val a: Node,
    val i: Array<out Node>,
    val o: Array<out Node>,
    val stateBefore: CausalNetState
) {

    /**
     * State after executing this activitiy binding given that the state before was [stateBefore]
     */
    val state: CausalNetState by lazy {
        val tmp = CausalNetStateImpl(stateBefore)
        i.forEach { tmp.remove(Dependency(it, a)) }   //do not replace with removeAll, it doesn't have appropriate semantics
        tmp.addAll(o.map { Dependency(a, it) })
        tmp
    }
}
