package processm.core.models.causalnet

import java.util.*
import kotlin.collections.HashSet

/**
 * A binding between dependencies outgoing from a node in a causal net
 */
class Split(_dependencies: Set<Dependency>) : Binding {

    override val dependencies: Set<Dependency> = Collections.unmodifiableSet(HashSet(_dependencies))

    init {
        if (dependencies.isEmpty()) {
            throw IllegalArgumentException("Binding specification cannot be empty")
        }
        val firstSource = dependencies.first().source
        if (dependencies.any { d -> d.source != firstSource }) {
            throw IllegalArgumentException("All the sources must point to the same activity instance")
        }
    }

    val source = dependencies.first().source

    override fun hashCode(): Int {
        return dependencies.hashCode()
    }

    override fun equals(obj: Any?): Boolean {
        return obj is Split && dependencies == obj.dependencies
    }
}