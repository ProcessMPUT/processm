package processm.core.models.metadata

/**
 * Map-based metadata provider
 */
class DefaultMetadataProvider<T : MetadataValue>(
    override val name: URN,
    private val _metadata: MutableMap<MetadataSubject, T> = HashMap()
) : MetadataProvider, Map<MetadataSubject, MetadataValue> by _metadata {

    override fun get(key: MetadataSubject): T {
        return _metadata.getValue(key)
    }

    override operator fun contains(a: MetadataSubject): Boolean {
        return a in _metadata
    }

    fun put(a: MetadataSubject, mtd: T) {
        _metadata[a] = mtd
    }

}