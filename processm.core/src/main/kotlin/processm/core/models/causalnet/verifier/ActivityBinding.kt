package processm.core.models.causalnet.verifier

import processm.core.helpers.MultiSet
import processm.core.models.causalnet.Node

/**
 * Computes cartesian product
 */
internal infix fun <A, B> Collection<A>.times(right: Collection<B>): List<Pair<A, B>> {
    return this.flatMap { a -> right.map { b -> a to b } }
}

/**
 * State is a multi-set of pending obligations (the PM book, Definition 3.10)
 */
typealias State = MultiSet<Pair<Node, Node>>

/**
 * Denotes the occurrence of activity [a] with input binding [i] and output binding [o]
 *
 * Follows the description from the PM book, right above Definition 3.9
 */
class ActivityBinding(
    val a: Node,
    val i: Collection<Node>,
    val o: Collection<Node>,
    private val stateBefore: State
) {

    /**
     * State after executing this activitiy binding given that the state before was [stateBefore]
     */
    val state: State by lazy {
        val tmp = State(stateBefore)
        tmp.removeAll(i times setOf(a))
        tmp.addAll(setOf(a) times o)
        tmp
    }
}