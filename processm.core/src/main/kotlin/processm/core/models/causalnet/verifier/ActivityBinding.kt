package processm.core.models.causalnet.verifier

import processm.core.models.causalnet.Node

internal infix fun <A, B> Collection<A>.times(right: Collection<B>): List<Pair<A, B>> {
    return this.flatMap { a -> right.map { b -> a to b } }
}

data class ActivityBinding(
    val a: Node,
    val i: Collection<Node>,
    val o: Collection<Node>,
    val stateBefore: Set<Pair<Node, Node>>
) {
    val state = stateBefore - (i times setOf(a)) + (setOf(a) times o)
}