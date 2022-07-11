package processm.core.models.metadata

interface MetadataProvider : Map<MetadataSubject, MetadataValue> {
    val name: URN
    operator fun contains(a: MetadataSubject): Boolean
    override fun get(key: MetadataSubject): MetadataValue
}