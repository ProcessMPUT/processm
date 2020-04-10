package processm.core.verifiers

import processm.core.models.processtree.Node
import processm.core.models.processtree.Model as ProcessTree

/**
 * Process Tree verifier
 */
class ProcessTreeVerifier : Verifier<ProcessTree> {
    /**
     * A block-structured models that are sound by construction.
     * We want to check only is correct tree model - each node has correct parent
     */
    override fun verify(model: ProcessTree): VerificationReport {
        // Tree without any node is correct tree
        if (model.root == null) return report(isTree = true)
        val root = model.root

        // Root with set parent is not allowed
        if (root!!.parent != null) return report(isTree = false)

        // Return checked parents of each children
        return report(isTree = root.children.all { hasCorrectParent(node = it, parent = root) })
    }

    /**
     * Check node has correct parent reference
     *
     * If correct if parent match AND all children with correct parents
     */
    private fun hasCorrectParent(node: Node, parent: Node): Boolean {
        return node.parent == parent && node.children.all { hasCorrectParent(node = it, parent = node) }
    }

    /**
     * Report structure builder
     * By definition some fields are true.
     */
    private fun report(isTree: Boolean = false): ProcessTreeVerificationReport {
        return ProcessTreeVerificationReport(
            /** By definition */
            isSafe = true,
            /** By definition */
            hasOptionToComplete = true,
            /** By definition */
            hasProperCompletion = true,
            /** By definition */
            noDeadParts = true,
            isTree = isTree
        )
    }
}