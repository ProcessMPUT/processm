package processm.core.models.causalnet

import processm.core.models.metadata.MetadataSubject

data class Dependency(val source: ActivityInstance, val target: ActivityInstance) : MetadataSubject