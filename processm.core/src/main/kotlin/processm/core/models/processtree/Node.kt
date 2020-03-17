package processm.core.models.processtree

import java.util.*
import kotlin.collections.ArrayList

abstract class Node(vararg nodes: Node) {
    protected val childrenInternal: MutableList<Node> = ArrayList()

    /**
     * Nodes of this node (children)
     */
    val children: List<Node> = Collections.unmodifiableList(childrenInternal)

    /**
     * Reference to own parent
     */
    var parent: Node? = null
        private set

    abstract val symbol: String

    init {
        nodes.forEach { node ->
            childrenInternal.add(node)
            node.parent = this
        }
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

    /**
     * Custom toString to present nodes in process tree as text
     */
    override fun toString(): String {
        val iterator = childrenInternal.iterator()
        val builder = StringBuilder()
        builder.append(symbol)

        if (iterator.hasNext()) {
            builder.append("(")

            while (iterator.hasNext()) {
                builder.append(iterator.next())
                if (iterator.hasNext())
                    builder.append(",")
            }

            builder.append(")")
        }

        return builder.toString()
    }
}