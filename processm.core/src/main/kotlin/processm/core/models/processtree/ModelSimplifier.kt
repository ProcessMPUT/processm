package processm.core.models.processtree

/**
 * This class is responsible for make process tree model simpler and generate it without extra activities
 */
class ModelSimplifier {
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
        if (model.root != null)
            reduceTauLeafsInModel(model.root)
    }

    private fun reduceTauLeafsInModel(node: Node) {
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

        // Apply action for each children
        node.childrenInternal.forEach {
            reduceTauLeafsInModel(it)
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