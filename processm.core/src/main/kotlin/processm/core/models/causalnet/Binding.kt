package processm.core.models.causalnet

import processm.core.models.metadata.MetadataSubject

/**
 * A collection of [Dependency] to serve as a base for [Split]/[Join]
 */
interface Binding : MetadataSubject {
    val dependencies: Set<Dependency>
}