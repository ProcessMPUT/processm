package processm.core.models.metadata

interface MetadataProvider {
    val name: String
    operator fun contains(a: MetadataSubject): Boolean
    fun get(a: MetadataSubject): MetadataValue
}