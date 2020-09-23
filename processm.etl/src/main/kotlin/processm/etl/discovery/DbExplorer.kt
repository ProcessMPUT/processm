package processm.etl.discovery

import java.io.Closeable

internal interface DbExplorer : Closeable {
    fun getClasses(): Set<Class>
    fun getRelationships(): Set<Relationship>
}

internal data class Attribute(val name: String, val type: String)
internal data class Class(val name: String, val attributes: List<Attribute>)
internal data class Relationship(val name: String, val sourceClass: Class, val targetClass: Class)
