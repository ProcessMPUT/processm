package processm.core.models.metadata

/**
 * An extension of [MetadataHandler] providing a collection of [MetadataProvider]s
 */
interface ModifiableMetadataHandler : MetadataHandler {
    val metadataProviders: Collection<MetadataProvider>
    fun addMetadataProvider(mp: MetadataProvider)
}