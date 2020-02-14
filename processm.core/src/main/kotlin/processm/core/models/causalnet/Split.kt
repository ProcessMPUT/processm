package processm.core.models.causalnet

/**
 * A binding between dependencies outgoing from a node in a causal net
 */
data class Split(override val dependencies: Set<Dependency>) : Binding {
    init {
        if (dependencies.isEmpty()) {
            throw IllegalArgumentException("Binding specification cannot be empty")
        }
        if (dependencies.any { d -> d.source != dependencies.first().source }) {
            throw IllegalArgumentException("All the sources must point to the same activity instance")
        }
    }

    val source = dependencies.first().source
}