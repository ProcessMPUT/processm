package processm.core.models.processtree

import java.util.*
import kotlin.collections.ArrayList

abstract class Node {
    internal val childrenInternal: MutableList<Node> = ArrayList()

    /**
     * Nodes of this node (children)
     */
    val children: List<Node> = Collections.unmodifiableList(childrenInternal)
}