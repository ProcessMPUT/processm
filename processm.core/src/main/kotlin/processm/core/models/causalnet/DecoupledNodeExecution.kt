package processm.core.models.causalnet

import processm.core.models.commons.Activity
import processm.core.models.commons.ActivityExecution

/**
 * Represents the possiblity of executing [activity] using [join] and [split]
 */
open class DecoupledNodeExecution(
    override val activity: Node,
    val join: Join?,
    val split: Split?
) : ActivityExecution, Activity {

    override val name: String
        get() = activity.name

    override val isSilent: Boolean
        get() = activity.isSilent

    @Deprecated("Use isSilent instead", replaceWith = ReplaceWith("isSilent"))
    override val isArtificial: Boolean
        get() = activity.isArtificial

    override fun execute() {
        throw UnsupportedOperationException()
    }

    override fun toString(): String = "$join -> $activity -> $split"
}
