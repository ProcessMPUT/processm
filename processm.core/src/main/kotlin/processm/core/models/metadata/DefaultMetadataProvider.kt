package processm.core.models.metadata

/**
 * Map-based metadata provider
 */
class DefaultMetadataProvider<T : MetadataValue>(override val name: String) : MetadataProvider {

    private val _metadata = HashMap<MetadataSubject, T>()

    override fun get(a: MetadataSubject): T {
        return _metadata.getValue(a)
    }

    override operator fun contains(a: MetadataSubject): Boolean {
        return a in _metadata
    }

    fun put(a: MetadataSubject, mtd: T) {
        _metadata[a] = mtd
    }

}