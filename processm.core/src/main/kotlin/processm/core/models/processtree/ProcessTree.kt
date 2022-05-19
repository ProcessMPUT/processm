package processm.core.models.processtree

import processm.core.helpers.mapToArray
import processm.core.models.commons.ProcessModel

/**
 * Process Tree model with `root` reference
 */
class ProcessTree(root: Node? = null) : ProcessModel {
    companion object {
        /**
         * Parses the prefix notation of the process tree into [ProcessTree].
         * It parses the output of the [toString] method.
         * Currently, this is a naive implementation intended for use in tests.
         *
         * @param string The string to parse.
         */
        fun parse(string: String): ProcessTree = ProcessTree(parseInternal(string))

        private fun parseInternal(string: String): Node {
            val trimmed = string.trim()
            require(trimmed.isNotEmpty())

            if (trimmed.length > 3 && trimmed[1] == '(' && trimmed[string.length - 1] == ')') {
                val ctor = when (trimmed[0]) {
                    '→' -> ::Sequence
                    '∧' -> ::Parallel
                    '⟲' -> ::RedoLoop
                    '×' -> ::Exclusive
                    else -> throw IllegalArgumentException("Parse error. Expected →, ∧, ⟲, ×; '${trimmed[0]}' found.")
                }
                val children = splitArguments(trimmed.substring(2, trimmed.length - 1)).mapToArray(::parseInternal)
                return ctor(children)
            }
            return if (trimmed == "τ") SilentActivity() else ProcessTreeActivity(trimmed)
        }

        private fun splitArguments(string: String) = sequence {
            val builder = StringBuilder()
            var depth = 0
            for (ch in string) {
                when (ch) {
                    ',' ->
                        if (depth == 0) {
                            yield(builder.toString())
                            builder.clear()
                        } else {
                            builder.append(ch)
                        }
                    '(' -> {
                        ++depth
                        builder.append(ch)
                    }
                    ')' -> {
                        --depth
                        builder.append(ch)
                    }
                    else -> builder.append(ch)
                }
            }
            yield(builder.toString())
        }
    }

    var root: Node? = root
        internal set

    override fun toString(): String {
        return root?.toString() ?: ""
    }

    /**
     * Check language equal between two models (two process tree)
     */
    fun languageEqual(other: ProcessTree): Boolean {
        return isLanguageEqual(root, other.root)
    }

    private fun isLanguageEqual(model: Node?, other: Node?): Boolean {
        // Both null == equal or the same references (the same models)
        if (model === other) return true
        // Only one null == not equal
        if (model == null || other == null) return false

        // Should store the same operators
        if (model.javaClass != other.javaClass) return false

        // Should contain the same number of children
        if (model.children.size != other.children.size) return false

        // Compare only if both activities
        if (model is ProcessTreeActivity)
            return model == other

        // Sequence should contain the same order of children
        if (model is Sequence) {
            for ((childInModel, childInOther) in model.children.zip(other.children)) {
                if (!isLanguageEqual(childInModel, childInOther)) return false
            }

            // Children match - on this level also match
            return true
        } else {
            if (model is RedoLoop) {
                // First node should be the same in both models
                if (!isLanguageEqual(model.children.firstOrNull(), other.children.firstOrNull())) return false
            }

            val allAttributes = HashSet<Node>()
            allAttributes.addAll(model.children)
            allAttributes.addAll(other.children)

            // All children should match - order not important now
            val childrenSetOther = other.children.toHashSet()
            for (childInModel in model.children) {
                for (childInOther in childrenSetOther) {
                    if (isLanguageEqual(childInModel, childInOther)) {
                        // Remove from all attributes set element from model and other
                        // x(A,B) and x(B,A) are logical equal but in set stored both - we need to remove both
                        allAttributes.remove(childInModel)
                        allAttributes.remove(childInOther)

                        // Remove element from set used to reduce the amount of calculations
                        childrenSetOther.remove(childInOther)

                        // Break loop - already match, second match not required
                        break
                    }
                }
            }

            return allAttributes.isEmpty()
        }
    }

    /**
     * A sequence of all nodes in this process tree. The order of nodes may or may not match the order of execution.
     */
    val allNodes: kotlin.sequences.Sequence<Node>
        get() {
            val r = root
            return if (r != null)
                sequenceOf(r) + r.childrenRecursive
            else
                emptySequence()
        }

    override val activities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = allNodes.filterIsInstance<ProcessTreeActivity>()

    override val startActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = root?.startActivities.orEmpty()

    override val endActivities: kotlin.sequences.Sequence<ProcessTreeActivity>
        get() = root?.endActivities.orEmpty()

    override val decisionPoints: kotlin.sequences.Sequence<InternalNode>
        get() = allNodes.filterIsInstance<InternalNode>()

    override val controlStructures: kotlin.sequences.Sequence<InternalNode>
        get() = decisionPoints

    override fun createInstance() = ProcessTreeInstance(this)


}
