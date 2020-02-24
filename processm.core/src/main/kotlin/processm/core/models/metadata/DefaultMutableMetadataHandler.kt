package processm.core.models.metadata

/**
 * A default, hash-map based implementation for metadata handler
 */
open class DefaultMutableMetadataHandler : MutableMetadataHandler {
    protected val _metadataProviders = HashMap<String, MetadataProvider>()
    override val availableMetadata = _metadataProviders.keys
    override fun getAllMetadata(subject: MetadataSubject): Map<String, MetadataValue> {
        return _metadataProviders.values.filter { subject in it }.map { it.name to it.get(subject) }.toMap()
    }

    override fun getMetadata(subject: MetadataSubject, metadata: String): MetadataValue {
        return _metadataProviders.getValue(metadata).get(subject)
    }

    override fun addMetadataProvider(mp: MetadataProvider) {
        if (mp.name in _metadataProviders)
            throw IllegalArgumentException("${mp.name} is already registered")
        _metadataProviders[mp.name] = mp
    }

    override val metadataProviders: Collection<MetadataProvider> = _metadataProviders.values
}