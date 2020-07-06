package processm.core.models.processtree

import processm.core.models.processtree.execution.ExecutionNode
import java.util.*

abstract class Node(vararg nodes: Node) {
    val activitiesSet = HashSet<String>()
    var currentTraceId = 0
    val analyzedTracesIds = HashSet<Int>()
    var precision: Double = 0.0

    internal val childrenInternal: MutableList<Node> = LinkedList()

    /**
     * Nodes of this node (children)
     */
    val children: List<Node> = Collections.unmodifiableList(childrenInternal)

    /**
     * Reference to own parent
     */
    var parent: Node? = null
        internal set

    /**
     * Symbol of the node. For operators should be graphic symbol, activity will use name.
     */
    abstract val symbol: String

    init {
        nodes.forEach { addChild(it) }
    }

    /**
     * Add child to children list
     * Assign parent as current node object
     */
    fun addChild(node: Node) {
        childrenInternal.add(node)
        node.parent = this
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
        return if (childrenInternal.isNotEmpty()) childrenInternal.joinToString(",", "$symbol(", ")") else symbol
    }

    internal val chilrenRecursive: kotlin.sequences.Sequence<Node>
        get() = sequence {
            yieldAll(childrenInternal)
            for (child in children)
                yieldAll(child.chilrenRecursive)
        }

    internal abstract val startActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
    internal abstract val endActivities: kotlin.sequences.Sequence<ProcessTreeActivity>

    internal abstract fun executionNode(parent: ExecutionNode?): ExecutionNode
}