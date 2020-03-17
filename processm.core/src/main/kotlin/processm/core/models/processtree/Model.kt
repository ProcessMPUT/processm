package processm.core.models.processtree

import processm.core.models.commons.AbstractModel

/**
 * Process Tree model with `root` reference
 */
class Model(val root: Node? = null) : AbstractModel {
    override fun toString(): String {
        return root?.toString() ?: ""
    }
}