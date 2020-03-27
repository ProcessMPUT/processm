package processm.core.models.causalnet

import processm.core.models.metadata.MetadataSubject

data class Dependency(val source: Node, val target: Node) : MetadataSubject