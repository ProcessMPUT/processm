package processm.core.models.causalnet.verifier

import processm.core.helpers.MultiSet
import processm.core.models.causalnet.Node

internal infix fun <A, B> Collection<A>.times(right: Collection<B>): List<Pair<A, B>> {
    return this.flatMap { a -> right.map { b -> a to b } }
}

typealias State = MultiSet<Pair<Node, Node>>

class ActivityBinding(
    val a: Node,
    val i: Collection<Node>,
    val o: Collection<Node>,
    private val stateBefore: State
) {
    val state: State by lazy {
        val tmp = State(stateBefore)
        tmp.removeAll(i times setOf(a))
        tmp.addAll(setOf(a) times o)
        tmp
    }
}