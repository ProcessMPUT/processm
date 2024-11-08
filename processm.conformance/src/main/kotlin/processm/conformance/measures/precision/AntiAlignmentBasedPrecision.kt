package processm.conformance.measures.precision

import processm.conformance.measures.AntiAlignmentBasedMeasures
import processm.conformance.measures.Measure
import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.AlignerFactory
import processm.conformance.models.antialignments.AntiAligner
import processm.conformance.models.antialignments.TwoPhaseDFS
import processm.core.log.hierarchical.Log
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.URN
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The anti-alignment-based precision measure as defined in Definition 7 in B.F. van Dongen,
 * A Unified Approach for Measuring Precision and Generalization Based on Anti-Alignments.
 */
class AntiAlignmentBasedPrecision(
    val base: AntiAlignmentBasedMeasures
) : Measure<Log, Double> {

    companion object {
        val URN: URN = URN("urn:processm:measures/anti_alignment_based_precision")
    }

    constructor(
        model: ProcessModel,
        alignerFactory: AlignerFactory = AlignerFactory { m, p, _ -> AStar(m, p) },
        antiAlignerFactory: (model: ProcessModel) -> AntiAligner = { TwoPhaseDFS(model) },
        alpha: Double = 0.5,
        pool: ExecutorService = Executors.newCachedThreadPool()
    ) : this(AntiAlignmentBasedMeasures(model, alignerFactory, antiAlignerFactory, alpha, alpha, pool))

    override val URN: URN
        get() = AntiAlignmentBasedPrecision.URN

    override fun invoke(artifact: Log): Double = base(artifact).precision

    internal val traceBasedPrecision: Double
        get() = base.traceBasedPrecision

    internal val logBasedPrecision: Double
        get() = base.logBasedPrecision

}
