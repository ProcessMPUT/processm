package processm.core.models.causalnet

import processm.helpers.mapToArray

/**
 * A binding between dependencies outgoing from a node in a causal net.
 *
 * Claims ownership of [dependencies], which should not be modified after construction of the binding.
 */
data class Split(override val dependencies: Set<Dependency>) : Binding {

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

    /**
     * Targets of all the dependencies of this split
     */
    val targets by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dependencies.mapToArray { d -> d.target }
    }

    override fun toString(): String {
        return "{${source} -> ${targets.joinToString()}}"
    }
}
