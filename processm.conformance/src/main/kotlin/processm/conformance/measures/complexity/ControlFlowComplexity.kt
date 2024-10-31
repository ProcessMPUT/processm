package processm.conformance.measures.complexity

import processm.conformance.measures.Measure
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.URN

/**
 * CFC = Control flow complexity.
 * See George Cardoso, Control-flow Complexity Measurement of Processes and Weyuker’s Properties, Proceedings of World
 * Academy of Science, Engineering and technology Volume 8 October 2005 ISSN 1307-6884
 */
object ControlFlowComplexity : Measure<ProcessModel, Int> {
    val URN: URN = URN("urn:processm:measures/control_flow_complexity")
    override fun invoke(artifact: ProcessModel): Int = artifact.controlStructures.sumOf { it.controlFlowComplexity }
}

typealias CFC = ControlFlowComplexity
