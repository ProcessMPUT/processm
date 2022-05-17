package processm.core.models.petrinet

import processm.core.models.commons.ControlStructure
import processm.core.models.commons.ControlStructureType

/**
 * A XOR-like control pattern in a Petri net.
 */
data class XOR(
    /**
     * The type of the control flow structure.
     * Either XorSplit or XorJoin.
     */
    override val type: ControlStructureType,
    /**
     * A place that begins (for split) or ends (for join) the XOR-like pattern.
     */
    val place: Place,
    /**
     * Transitions that succeed the split or precede the join.
     */
    val transitions: Collection<Transition>
) : ControlStructure {
    init {
        require(type == ControlStructureType.XorSplit || type == ControlStructureType.XorJoin)
    }

    override val controlFlowComplexity: Int
        get() = transitions.size
}
