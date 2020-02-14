package processm.core.models.causalnet

/**
 * A binding between dependencies incoming into a node in a causal net
 */
data class Join(override val dependencies: Set<Dependency>) : Binding {
    init {
        if (dependencies.isEmpty()) {
            throw IllegalArgumentException("Binding specification cannot be empty")
        }
        if (!(dependencies.isNotEmpty() && dependencies.all { d -> d.target == dependencies.first().target })) {
            throw IllegalArgumentException("All the targets must point to the same activity instance")
        }
    }

    val target = dependencies.first().target
}