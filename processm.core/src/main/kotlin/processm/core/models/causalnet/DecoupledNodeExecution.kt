package processm.core.models.causalnet

import processm.core.models.commons.ActivityExecution

/**
 * Represents the possiblity of executing [activity] using [join] and [split]
 */
open class DecoupledNodeExecution internal constructor(
    override val activity: Node,
    val join: Join?,
    val split: Split?
) : ActivityExecution {

    override fun execute() {
        throw UnsupportedOperationException()
    }
}