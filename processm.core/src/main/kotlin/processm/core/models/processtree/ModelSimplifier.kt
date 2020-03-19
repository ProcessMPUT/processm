package processm.core.models.processtree

/**
 * This class is responsible for make process tree model simpler and generate it without extra activities
 */
class ModelSimplifier {
    /**
     * Reduce tau leafs in SEQUENCE operator only.
     * WARNING: This action can modify internal structure of given process tree model!
     *
     * For example in tree →(A,τ,B) activity τ can be removed and we can generate →(A,B)
     * Model with →(A,τ) will be simplify to →(A) => no reduction to single activity!
     * Reduction can be executed also in parallel operator.
     * In exclusive choice operator can use reduction if one silent activity will be still in tree.
     *
     * For redo loop operator we can simplify body part:
     * ⟲(τ,A,τ,τ,τ,C,τ) will be replaced by ⟲(τ,A,τ,C)
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