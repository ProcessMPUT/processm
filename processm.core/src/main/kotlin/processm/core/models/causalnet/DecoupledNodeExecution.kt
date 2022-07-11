package processm.core.models.causalnet

import processm.core.models.commons.Activity
import processm.core.models.commons.ActivityExecution
import java.util.*

/**
 * Represents the possibility of executing [activity] using [join] and [split]
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

    override val isArtificial: Boolean
        get() = activity.isArtificial

    override fun equals(obj: Any?): Boolean =
        obj is DecoupledNodeExecution && activity == obj.activity && join == obj.join && split == obj.split

    override fun hashCode(): Int = Objects.hash(activity, join, split)

    override fun execute() {
        throw UnsupportedOperationException()
    }

    override fun toString(): String = "$join -> $activity -> $split"
}