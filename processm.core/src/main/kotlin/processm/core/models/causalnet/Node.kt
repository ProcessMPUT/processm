package processm.core.models.causalnet

import processm.core.models.metadata.MetadataSubject

/**
 * Represents an instance activity, i.e., a node in a causal net.
 *
 * By default [instanceId] is empty, in order to allow easy ignoring the possibility of having multiple instances of
 * a single activity and allowing only for a single instance of each activity.
 */
data class Node(val activity: String, val instanceId: String = "", val special: Boolean = false) : MetadataSubject