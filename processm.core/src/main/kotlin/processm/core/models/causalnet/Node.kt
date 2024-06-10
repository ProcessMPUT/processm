package processm.core.models.causalnet

import kotlinx.serialization.Serializable
import processm.core.models.commons.Activity
import java.util.*

/**
 * Represents an instance activity, i.e., a node in a causal net.
 *
 * By default [instanceId] is empty, in order to allow easy ignoring the possibility of having multiple instances of
 * a single activity and allowing only for a single instance of each activity.
 */
@Serializable
data class Node(
    override val name: String,
    val instanceId: String = "",
    override val isSilent: Boolean = false,
    @Deprecated("Use isSilent instead", replaceWith = ReplaceWith("isSilent"))
    override val isArtificial: Boolean = false,
) : Activity {

    init {
        assert(!isArtificial) { "Node.isArtificial is deprecated and should be set to the default of false." }
    }

    private val hash: Int by lazy {
        Objects.hash(name, instanceId, isSilent)
    }

    @Deprecated("Use name instead", replaceWith = ReplaceWith("name"))
    val activity: String
        get() = name

    override fun toString(): String {
        return name + (if (instanceId.isNotEmpty()) "($instanceId)" else "") + (if (isSilent) "*" else "")
    }

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Node) return false
        return name == other.name && instanceId == other.instanceId && isSilent == other.isSilent
    }
}
