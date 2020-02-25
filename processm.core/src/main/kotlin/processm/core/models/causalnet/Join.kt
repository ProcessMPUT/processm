package processm.core.models.causalnet

import java.util.*
import kotlin.collections.HashSet

/**
 * A binding between dependencies incoming into a node in a causal net
 */
class Join(_dependencies: Set<Dependency>) : Binding {

    override val dependencies: Set<Dependency> = Collections.unmodifiableSet(HashSet(_dependencies))

    init {
        if (dependencies.isEmpty()) {
            throw IllegalArgumentException("Binding specification cannot be empty")
        }
        val firstTarget = dependencies.first().target
        if (dependencies.any { d -> d.target != firstTarget }) {
            throw IllegalArgumentException("All the targets must point to the same activity instance")
        }
    }

    val target = dependencies.first().target

    override fun hashCode(): Int {
        return dependencies.hashCode()
    }

    override fun equals(obj: Any?): Boolean {
        return obj is Join && dependencies == obj.dependencies
    }
}