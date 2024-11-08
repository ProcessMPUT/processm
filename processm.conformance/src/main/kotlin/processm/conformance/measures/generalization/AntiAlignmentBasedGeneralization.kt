package processm.conformance.measures.generalization

import processm.conformance.measures.AntiAlignmentBasedMeasures
import processm.conformance.measures.Measure
import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.AlignerFactory
import processm.conformance.models.antialignments.AntiAligner
import processm.conformance.models.antialignments.TwoPhaseDFS
import processm.core.log.hierarchical.Log
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.URN

/**
 * The anti-alignment-based generalization measure as defined in Definition 7 in B.F. van Dongen,
 * A Unified Approach for Measuring Precision and Generalization Based on Anti-Alignments.
 */
class AntiAlignmentBasedGeneralization(
    val base: AntiAlignmentBasedMeasures
) : Measure<Log, Double> {

    companion object {
        val URN: URN = URN("urn:processm:measures/anti_alignment_based_generalization")
    }

    constructor(
        model: ProcessModel,
        alignerFactory: AlignerFactory = AlignerFactory { m, p, _ -> AStar(m, p) },
        antiAligner: AntiAligner = TwoPhaseDFS(model),
        alpha: Double = 0.5
    ) : this(AntiAlignmentBasedMeasures(model, alignerFactory, antiAligner, alpha, alpha))

    override val URN: URN
        get() = AntiAlignmentBasedGeneralization.URN

    override fun invoke(artifact: Log): Double = base(artifact).generalization

    internal val traceBasedGeneralization: Double
        get() = base.traceBasedGeneralization

    internal val logBasedGeneralization: Double
        get() = base.logBasedGeneralization
}
