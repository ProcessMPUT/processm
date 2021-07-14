package processm.core.models.causalnet

import processm.core.models.metadata.MetadataSubject
import java.util.*

data class Dependency(val source: Node, val target: Node) : MetadataSubject {

    private val hash:Int by lazy {
        Objects.hash(source, target)
    }

    override fun toString(): String {
        return "[$source -> $target]"
    }

    override fun hashCode(): Int = hash
}