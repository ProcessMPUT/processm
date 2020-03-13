package processm.core.models.causalnet

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
     * Sources of all the dependencies of this join
     */
    val sources by lazy {
        dependencies.map { d -> d.source }.toSet()
    }
}