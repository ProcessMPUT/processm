package processm.core.models.processtree

import java.util.*
import kotlin.collections.ArrayList

abstract class Node(vararg nodes: Node) {
    internal val childrenInternal: MutableList<Node> = ArrayList()

    /**
     * Nodes of this node (children)
     */
    val children: List<Node> = Collections.unmodifiableList(childrenInternal)

    init {
        childrenInternal.addAll(nodes)
    }

    operator fun plus(other: Node): Node {
        childrenInternal.add(other)
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Node
        return childrenInternal == other.childrenInternal
    }

    override fun hashCode(): Int {
        return childrenInternal.hashCode()
    }
}