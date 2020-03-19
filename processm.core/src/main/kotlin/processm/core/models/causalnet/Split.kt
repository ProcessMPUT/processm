package processm.core.models.causalnet

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
    val targets by lazy {
        dependencies.map { d -> d.target }.toSet()
    }
}