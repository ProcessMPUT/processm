package processm.core.models.causalnet

import kotlinx.serialization.Serializable
import processm.core.models.commons.Activity
import processm.core.models.metadata.MetadataSubject
import java.util.*

/**
 * Represents an instance activity, i.e., a node in a causal net.
 *
 * By default [instanceId] is empty, in order to allow easy ignoring the possibility of having multiple instances of
 * a single activity and allowing only for a single instance of each activity.
 */
@Serializable
data class Node(
    val activity: String,
    val instanceId: String = "",
    override val isSilent: Boolean = false,
    @Deprecated("Use isSilent instead", replaceWith = ReplaceWith("isSilent"))
    override val isArtificial: Boolean = false,
) : MetadataSubject,
    Activity {

    init {
        assert(!isArtificial) { "Node.isArtificial is deprecated and should be set to the default of false." }
    }

    private val hash: Int by lazy {
        Objects.hash(activity, instanceId, isSilent)
    }

    override val name: String
        get() = activity

    override fun toString(): String {
        return activity + (if (instanceId.isNotEmpty()) "($instanceId)" else "") + (if (isSilent) "*" else "")
    }

    override fun hashCode(): Int = hash
}
