package processm.core.models.petrinet

import processm.core.models.commons.ControlStructure
import processm.core.models.commons.ControlStructureType

data class Causality(
    val previousTransition: Transition,
    val nextTransition: Transition
) : ControlStructure {
    override val type: ControlStructureType
        get() = ControlStructureType.Causality
    override val controlFlowComplexity: Int
        get() = 1
}
