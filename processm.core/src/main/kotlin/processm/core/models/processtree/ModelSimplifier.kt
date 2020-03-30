package processm.core.models.processtree

/**
 * This class is responsible for make process tree model simpler and generate it without extra activities
 */
class ModelSimplifier {
    /**
     * Simplify process tree
     * WARNING: This action can modify internal structure of given process tree model!
     *
     * Actions:
     * * Remove operators with single activity and replace it with this activity
     * * Remove extra τ activities based on operator meaning
     * * Replace nested operators with one operator - can be prepared if operator Parallel or Exclusive
     *   (for example op(op(a, b, c), d) == op(a, b, c, d) if op == ∧ or ×
     * * Replace nodes without children by τ activity
     */
    fun simplify(model: Model) {
        val root = model.root

        // Ignore
        if (root != null && root !is Activity) {
            // Simplify process tree
            simplifyProcessTree(root)

            // Calculate how many children we have after clean up tree
            val rootChildrenSize = root.childrenInternal.size

            // No children - can replace root with silent activity
            if (rootChildrenSize == 0) {
                model.root = SilentActivity()
            }

            // Operator with one child
            if (rootChildrenSize == 1) {
                model.root = root.childrenInternal.first()
                model.root!!.parent = null
            }
        }
    }

    /**
     * Drops redundant [SilentActivity] leaves if it does not change the semantics of the process tree.
     * WARNING: This action can modify the internal structure of the given process tree!
     *
     * For example:
     * * The tree →(A,τ,B) will be reduced to →(A,B) without changing its semantics.
     * * The tree →(A,τ) will be reduced to →(A). A further reduction to a single activity does not apply.
     * * The tree ∧(A,τ,B) will be reduced to ∧(A,B).
     * * The tree ×(A,τ,B,τ) will be reduced to ×(A,τ,B), such that at least one τ remains.
     * * The tree ⟲(τ,A,τ,τ,τ,C,τ) will be reduced to ⟲(τ,A,τ,C), such that at least one τ remains as non-first child.
     */
    fun reduceTauLeafs(model: Model) {
        if (model.root != null) {
            // Reduce tree
            reduceTauLeafsInModel(model.root!!)

            // Simplify node
            reduceTauActivitiesInNode(model.root!!)
        }
    }

    private fun simplifyProcessTree(node: Node) {
        // Edge case - redo loop with only silent activities can be replaced by just one silent activity
        if (node is RedoLoop) {
            if (node.childrenInternal.all { it is SilentActivity }) {
                val iter = node.childrenInternal.iterator()

                // Skip first silent activity
                if (iter.hasNext())
                    iter.next()

                // Remove remaining part
                while (iter.hasNext()) {
                    iter.next()
                    iter.remove()
                }
            }
        }

        // For each child try to simplify model
        node.childrenInternal.forEach { child ->
            simplifyProcessTree(child)
        }

        node.childrenInternal.forEach { child ->
            val childrenInChildNode = child.childrenInternal.size

            // Replace empty operator with silent activity
            if (childrenInChildNode == 0 && child !is Activity) {
                node.replaceChild(replaced = child, replacement = SilentActivity())
            }

            // Move one level up alone child
            if (childrenInChildNode == 1) {
                node.replaceChild(replaced = child, replacement = child.childrenInternal.first())
            }
        }

        if (node is Sequence || node is Exclusive || node is Parallel) {
            var changedNodes = true

            // Each node with the same symbol like node can be moved up
            while (changedNodes) {
                // Set no changes in current iteration
                changedNodes = false

                // To prevent concurrent modification on iterator - we will iterate multiple times over children
                val iterator = node.childrenInternal.iterator()
                var index = 0

                while (iterator.hasNext()) {
                    val child = iterator.next()

                    // If is operator and operator's type like node's type
                    if (child !is Activity && node.symbol == child.symbol) {
                        // Remove old node from children list
                        iterator.remove()

                        // Add children from child to node
                        node.childrenInternal.addAll(index, child.childrenInternal)

                        child.childrenInternal.forEach { n ->
                            // Change parent reference
                            n.parent = node

                            // Update index reference
                            index++
                        }

                        // Break current iteration - prevent concurrent modification error
                        changedNodes = true
                        break
                    }

                    // Update index reference - iteration complete
                    index++
                }
            }
        }

        // Simplify node after operations - remove extra silent activities
        reduceTauActivitiesInNode(node)
    }

    private fun reduceTauLeafsInModel(node: Node) {
        reduceTauActivitiesInNode(node)


        // Apply action for each children
        node.childrenInternal.forEach {
            reduceTauLeafsInModel(it)
        }
    }

    private fun reduceTauActivitiesInNode(node: Node) {
        when (node) {
            is Sequence, is Parallel -> {
                var childrenCount = node.childrenInternal.size
                val iterator = node.childrenInternal.iterator()

                // Iterate over children
                while (iterator.hasNext()) {
                    // If node is silent activity AND this is not only child
                    if (iterator.next() is SilentActivity && childrenCount > 1) {
                        // Remove silent activity and decrement total number of not removed children in analyzed node
                        iterator.remove()
                        childrenCount--
                    }
                }
            }
            is Exclusive -> {
                val iterator = node.childrenInternal.iterator()
                removeDuplicatedTauLeafs(iterator)
            }
            is RedoLoop -> {
                val iterator = node.childrenInternal.iterator()

                // Ignore the first element - it can not be simplified
                if (iterator.hasNext())
                    iterator.next()

                removeDuplicatedTauLeafs(iterator)
            }
        }
    }

    private fun removeDuplicatedTauLeafs(iterator: MutableIterator<Node>) {
        var alreadySeenSilentActivity = false
        while (iterator.hasNext()) {
            // If node is silent activity AND already seen silent activity
            if (iterator.next() is SilentActivity) {
                if (alreadySeenSilentActivity) {
                    // Remove silent activity
                    iterator.remove()
                } else {
                    // Mark silent activity as already seen in operator - each next we can remove
                    alreadySeenSilentActivity = true
                }
            }
        }
    }
}