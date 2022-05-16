package processm.conformance.rca.ml

import processm.conformance.rca.Feature

interface DecisionTreeModel : Model {

    companion object {
        private const val TAB: String = "  "
    }

    /**
     * A decision in an internal node of a decision tree, either [CategoricalSplit] or [ContinuousSplit]
     */
    interface Split {

        /**
         * The feature the decision is based upon
         */
        val feature: Feature
    }

    /**
     * If the value of the [feature] is in [left], go to the left child.
     * If the value of the [feature] is in [right], go to the right child.
     */
    data class CategoricalSplit<V>(override val feature: Feature, val left: Set<V?>, val right: Set<V?>) : Split

    /**
     * If the value of the [feature] is no greater than the [threshold], go to the left child.
     * Otherwise, go to the right child.
     */
    data class ContinuousSplit<V>(override val feature: Feature, val threshold: V) : Split

    /**
     * A node of a decision tree, either [InternalNode] or [Leaf]
     */
    interface Node {
        fun toMultilineString(indent: String = ""): String
    }

    /**
     * An internal node of a decision tree, consisting of three parts:
     * @param [split] How to made the decision where to go
     * @param [left] The left child
     * @param [right] The right child
     * @see [CategoricalSplit]
     * @see [ContinuousSplit]
     */
    data class InternalNode(val split: Split, val left: Node, val right: Node) : Node {
        override fun toMultilineString(indent: String): String = buildString {
            append(indent)
            append(split.feature.name)
            if (split is CategoricalSplit<*>) {
                append(" IN ")
                append(split.left)
            } else {
                check(split is ContinuousSplit<*>)
                append(" <= ")
                append(split.threshold)
            }
            appendLine()
            append(left.toMultilineString("$indent$TAB"))
            append(indent)
            append("else // ")
            append(split.feature.name)
            if (split is CategoricalSplit<*>) {
                append(" IN ")
                append(split.right)
            } else {
                check(split is ContinuousSplit<*>)
                append(" > ")
                append(split.threshold)
            }
            appendLine()
            append(right.toMultilineString("$indent$TAB"))
        }
    }

    /**
     * A leaf (i.e., final decision) in a decision tree.
     * @param decision The class to assign the considered example to
     * @param impurity A quality measure, the lower the better
     */
    data class Leaf(val decision: Boolean, val impurity: Double) : Node {
        override fun toMultilineString(indent: String): String = "${indent}label = $decision (impurity: $impurity)\n"
    }

    /**
     * The root of the tree, usually [InternalNode]
     */
    val root: Node

    /**
     * The depth of the tree
     */
    val depth: Int

    /**
     * A debug function to pretty-print the tree
     */
    fun toMultilineString() = root.toMultilineString()
}