package processm.core.models.metadata

/**
 * Metadata consisting of a single value of an arbitrary type.
 */
class SingleValueMetadata<T>(val value: T) : MetadataValue