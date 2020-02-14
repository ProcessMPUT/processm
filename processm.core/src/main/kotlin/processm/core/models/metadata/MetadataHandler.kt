package processm.core.models.metadata

/**
 * An interface for a (composite) object able to provide metadata for its components. The components must all implement [MetadataSubject] interface
 */
interface MetadataHandler {
    val availableMetadata: Set<String>
    fun getAllMetadata(subject: MetadataSubject): Map<String, MetadataValue>
    fun getMetadata(subject: MetadataSubject, metadata: String): MetadataValue
}