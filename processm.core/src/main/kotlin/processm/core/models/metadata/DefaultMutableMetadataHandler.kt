package processm.core.models.metadata

/**
 * A default, hash-map based implementation for metadata handler
 */
open class DefaultMutableMetadataHandler : MutableMetadataHandler {
    protected val _metadataProviders = HashMap<URN, MetadataProvider>()
    override val availableMetadata = _metadataProviders.keys
    override fun getAllMetadata(subject: MetadataSubject): Map<URN, MetadataValue> {
        return _metadataProviders.values.filter { subject in it }.map { it.name to it.get(subject) }.toMap()
    }

    override fun getMetadata(subject: MetadataSubject, metadata: URN): MetadataValue {
        return _metadataProviders.getValue(metadata).get(subject)
    }

    override fun getProvider(metadata: URN): MetadataProvider = _metadataProviders.getValue(metadata)

    override fun addMetadataProvider(mp: MetadataProvider) {
        if (mp.name in _metadataProviders)
            throw IllegalArgumentException("${mp.name} is already registered")
        _metadataProviders[mp.name] = mp
    }

    override val metadataProviders: Collection<MetadataProvider> = _metadataProviders.values
}