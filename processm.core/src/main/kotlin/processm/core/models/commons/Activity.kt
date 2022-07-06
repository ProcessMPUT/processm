package processm.core.models.commons

import processm.core.models.metadata.MetadataSubject

/**
 * An activity of the model, identified by [name]
 */
interface Activity : MetadataSubject {
    /**
     * The name of this activity.
     */
    val name: String

    /**
     * Marks the silent activity, i.e., the activity that leaves no trace of execution.
     */
    val isSilent: Boolean
        get() = false

    /**
     * Marks the silent activity that does not exist in the model but was added on the fly during execution to simplify
     * workflow.
     */
    @Deprecated("Use isSilent instead", replaceWith = ReplaceWith("isSilent"))
    val isArtificial: Boolean
        get() = false
}
