package processm.core.models.causalnet

import processm.core.models.commons.AbstractActivityExecution

open class DecoupledNodeExecution internal constructor(
    override val activity: Node,
    val join: Join?,
    val split: Split?
) : AbstractActivityExecution {

    override fun execute() {
        throw UnsupportedOperationException()
    }
}