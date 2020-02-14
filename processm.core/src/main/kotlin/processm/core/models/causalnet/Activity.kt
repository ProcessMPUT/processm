package processm.core.models.causalnet

import processm.core.models.metadata.MetadataSubject

/**
 * Represents an activity in a causal net, but is not directly used by the causal net.
 *
 * @param name Name of the activity
 * @param special Denotes special activities, such as artificial start or end
 */
data class Activity(val name: String, val special: Boolean = false) : MetadataSubject