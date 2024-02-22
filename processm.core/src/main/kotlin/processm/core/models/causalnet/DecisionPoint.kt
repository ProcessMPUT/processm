package processm.core.models.causalnet

import processm.core.models.commons.Activity
import processm.core.models.commons.ControlStructure
import processm.core.models.commons.ControlStructureType
import processm.core.models.commons.DecisionPoint
import processm.helpers.allSubsets

/**
 * A CausalNet decision point, i.e., choice of a join or a split for the give [node]
 */
class DecisionPoint(
    val node: Node,
    private val bindings: Set<Binding>,
    split: Boolean,
    override val previousActivities: Collection<Activity> = emptySet()
) : DecisionPoint, ControlStructure {
    override val possibleOutcomes: List<BindingDecision> = bindings.map { BindingDecision(it, this) }
    override val type: ControlStructureType
    override val controlFlowComplexity: Int
        get() = possibleOutcomes.size

    init {
        require(bindings.isNotEmpty())

        val first = bindings.first()
        if (bindings.size == 1 && first.size == 1) {
            type = ControlStructureType.Causality
        } else if (bindings.size == 1 && first.size > 1) {
            type = if (split) ControlStructureType.AndSplit else ControlStructureType.AndJoin
        } else if (bindings.all { it.size == 1 }) {
            assert(bindings.size > 1)
            type = if (split) ControlStructureType.XorSplit else ControlStructureType.XorJoin
        } else {
            // OR if bindings include all subsets of dependent nodes
            val dependencies = HashSet<Node>()
            for (binding in bindings) {
                for (dependency in binding.dependencies) {
                    dependencies.add(if (split) dependency.target else dependency.source)
                }
            }
            type =
                if (bindings.size == (1 shl (dependencies.size)) - 1 && bindings == dependencies.allSubsets(excludeEmpty = true)) {
                    if (split) ControlStructureType.OrSplit else ControlStructureType.OrJoin
                } else {
                    if (split) ControlStructureType.OtherSplit else ControlStructureType.OtherJoin
                }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is processm.core.models.causalnet.DecisionPoint) return false

        if (node != other.node) return false
        if (bindings != other.bindings) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = node.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}
