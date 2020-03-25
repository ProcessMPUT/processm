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
     * Reduction can be executed also if parallel operator.
     */
    fun reduceTauLeafs(model: Model) {
        if (model.root != null)
            reduceTauLeafsInModel(model.root)
    }

    private fun reduceTauLeafsInModel(node: Node) {
        // Apply reduce only if sequence operator
        if (node is Sequence || node is Parallel) {
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

        // Apply action for each children
        node.childrenInternal.forEach {
            reduceTauLeafsInModel(it)
        }
    }
}