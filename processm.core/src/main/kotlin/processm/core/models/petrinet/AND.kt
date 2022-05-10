package processm.core.models.petrinet

import processm.core.models.commons.ControlStructure
import processm.core.models.commons.ControlStructureType

/**
 * An AND-like control pattern in a Petri net.
 */
data class AND(
    /**
     * The type of the control flow structure.
     * Either AndSplit or AndJoin.
     */
    override val type: ControlStructureType,
    /**
     * The places that succeed the split or precede the join.
     */
    val places: Collection<Place>,
    /**
     * The transition that begins (split) or ends (join) the AND-like pattern.
     */
    val transition: Transition
) : ControlStructure {
    init {
        require(type == ControlStructureType.AndJoin || type == ControlStructureType.AndSplit)
    }

    override val controlFlowComplexity: Int
        get() = 1
}
