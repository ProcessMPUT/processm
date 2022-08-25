package processm.core.models.causalnet

import kotlinx.serialization.Serializable
import processm.core.models.commons.Arc
import processm.core.models.metadata.MetadataSubject
import java.util.*

@Serializable
data class Dependency(override val source: Node, override val target: Node) : MetadataSubject, Arc {

    private val hash: Int by lazy {
        Objects.hash(source, target)
    }

    override fun toString(): String {
        return "[$source -> $target]"
    }

    override fun hashCode(): Int = hash
}