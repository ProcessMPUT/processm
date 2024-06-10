package processm.core.models.causalnet

import processm.helpers.mapToArray
import processm.helpers.mapToSortedArray

/**
 * A binding between dependencies incoming into a node in a causal net.
 *
 * Claims ownership of [dependencies], which should not be modified after construction of the binding.
 */
data class Join(override val dependencies: Set<Dependency>) : Binding {
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

    /**
     * Sources of all the dependencies of this join. This array is guaranteed to be sorted by source activity name.
     */
    val sources by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dependencies.mapToSortedArray { d -> d.source }
    }

    override val dependenciesAsArray: Array<out Dependency> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dependencies.mapToArray { it }
    }

    override fun toString(): String {
        return "{${sources.joinToString()} -> ${target}}"
    }
}
