package processm.core.models.metadata

/**
 * An interface for a (composite) object able to provide metadata for its components. The components must all implement [MetadataSubject] interface
 */
interface MetadataHandler {
    val availableMetadata: Set<URN>
    fun getAllMetadata(subject: MetadataSubject): Map<URN, MetadataValue>
    fun getMetadata(subject: MetadataSubject, metadata: URN): MetadataValue
}