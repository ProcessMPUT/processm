package processm.core.models.causalnet

import processm.core.models.commons.AbstractActivityExecution

/**
 * Represents the possiblity of executing [activity] using [join] and [split]
 */
open class DecoupledNodeExecution internal constructor(
    override val activity: Node,
    val join: Join?,
    val split: Split?
) : AbstractActivityExecution {

    override fun execute() {
        throw UnsupportedOperationException()
    }
}