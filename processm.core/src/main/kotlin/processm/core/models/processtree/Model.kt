package processm.core.models.processtree

import processm.core.models.commons.AbstractModel

/**
 * Process Tree model with `root` reference
 */
class Model(val root: Node? = null) : AbstractModel {
    override fun toString(): String {
        return root?.toString() ?: ""
    }

    fun languageEqual(other: Model): Boolean {
        return isLanguageEqual(root, other.root)
    }

    private fun isLanguageEqual(model: Node?, other: Node?): Boolean {
        // Both null == equal
        if (model == null && other == null) return true
        // Only one null == not equal
        if (model == null || other == null) return false

        // Should store the same operators
        if (model.javaClass != other.javaClass) return false

        // Should contain the same number of children
        if (model.children.size != other.children.size) return false

        // Compare only if both activities
        if (model is Activity)
            return model == other

        // Sequence should contain the same order of children
        if (model is Sequence) {
            for ((childInModel, childInOther) in model.children.zip(other.children)) {
                if (!isLanguageEqual(childInModel, childInOther)) return false
            }

            // Children match - on this level also match
            return true
        } else {
            val allAttributes = HashSet<Node>()
            allAttributes.addAll(model.children)
            allAttributes.addAll(other.children)

            if (model is RedoLoop) {
                // First node should be the same in both models
                if (!isLanguageEqual(model.children.firstOrNull(), other.children.firstOrNull())) return false
            }

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
}