package processm.core.models.causalnet

import processm.core.models.metadata.MetadataSubject

/**
 * A collection of [Dependency] to serve as a base for [Split]/[Join]
 */
interface Binding : MetadataSubject {

    /**
     * True if [dep] is in [dependencies], false otherwise
     */
    operator fun contains(dep: Dependency): Boolean = dep in dependencies

    /**
     * Number of dependencies in this binding, i.e., [dependencies].[size]
     */
    val size: Int
        get() = dependencies.size

    val dependencies: Set<Dependency>
}