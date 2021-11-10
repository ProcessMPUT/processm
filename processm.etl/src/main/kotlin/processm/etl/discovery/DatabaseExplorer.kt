package processm.etl.discovery

import java.io.Closeable

interface DatabaseExplorer : Closeable {
    fun getClasses(): Set<Class>
    fun getRelationships(): Set<Relationship>
}

data class Attribute(val name: String, val type: String, val isPartOfForeignKey: Boolean)
data class Class(val name: String, val attributes: List<Attribute>)
data class Relationship(val name: String, val sourceClass: Class, val targetClass: Class, val sourceColumnName: String)
