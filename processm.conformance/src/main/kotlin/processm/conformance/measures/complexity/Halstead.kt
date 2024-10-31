package processm.conformance.measures.complexity

import processm.conformance.measures.Measure
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.URN

/**
 * Calculates Halstead complexity metric.
 * See J. Cardoso, J. Mendling2, G. Neumann, and H.A. Reijers, A Discourse on Complexity of Process Models,
 * BPM 2006 Workshops, LNCS 4103, pp. 115–126, 2006
 * and
 * M. H. Halstead. Elements of Software Science. Elsevier, Amsterdam, 1987.
 */
object Halstead : Measure<ProcessModel, HalsteadComplexityMetric> {
    val uniqueOperatorsURN: URN = URN("urn:processm:measures/halstead/unique_operators")
    val uniqueOperandsURN: URN = URN("urn:processm:measures/halstead/unique_operands")
    val totalOperatorsURN: URN = URN("urn:processm:measures/halstead/total_operators")
    val totalOperandsURN: URN = URN("urn:processm:measures/halstead/total_operands")

    override fun invoke(artifact: ProcessModel): HalsteadComplexityMetric = HalsteadComplexityMetric(
        (artifact.activities + artifact.controlStructures).distinct().count(),
        artifact.activities.distinct().count(),
        (artifact.activities + artifact.controlStructures).count(),
        artifact.activities.count()
    )
}
