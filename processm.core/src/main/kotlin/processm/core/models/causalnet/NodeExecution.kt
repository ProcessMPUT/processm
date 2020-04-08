package processm.core.models.causalnet

import processm.core.models.commons.AbstractActivityExecution

data class NodeExecution internal constructor(
    override val activity: Node,
    val instance: MutableModelInstance,
    val join: Join?,
    val split: Split?
) : AbstractActivityExecution {

    override fun execute() = instance.execute(join, split)
}