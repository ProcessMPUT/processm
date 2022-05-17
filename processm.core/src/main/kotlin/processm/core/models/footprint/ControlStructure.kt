package processm.core.models.footprint

import processm.core.models.commons.ControlStructureType

data class ControlStructure(
    override val type: ControlStructureType,
    val previousActivity: FootprintActivity,
    val nextActivity: FootprintActivity
) : processm.core.models.commons.ControlStructure {
    override val controlFlowComplexity: Int
        get() = 1
}
