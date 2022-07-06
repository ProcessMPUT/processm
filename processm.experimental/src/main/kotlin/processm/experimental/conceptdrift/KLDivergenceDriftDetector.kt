package processm.experimental.conceptdrift

import processm.conformance.conceptdrift.DriftDetector
import processm.conformance.conceptdrift.allExcept
import processm.conformance.conceptdrift.cvFolds
import processm.conformance.conceptdrift.estimators.KernelDensityEstimator
import processm.conformance.conceptdrift.numerical.integration.Integrator
import processm.conformance.conceptdrift.numerical.integration.MidpointIntegrator
import processm.conformance.conceptdrift.transpose
import processm.conformance.models.alignments.Alignment
import processm.conformance.rca.Feature
import processm.conformance.rca.propositionalize
import processm.core.logging.debug
import processm.core.logging.logger
import processm.experimental.conceptdrift.statisticaldistance.NaiveKLDivergence
import java.util.*
import kotlin.math.absoluteValue

private fun List<Double>.toKDF(): KernelDensityEstimator {
    //TODO handle NaNs
    val kdf = KernelDensityEstimator()
    kdf.fit(this)
    return kdf
}

/**
 * Every [observe]d [Alignment] is used to update the data model. These [Alignment]s that have [Alignment.cost]=0, are
 * also used to update the process model. If, at any time, `abs(KLDivergence/baselineKLDivergence - 1)>threshold`, a concept drift is reported.
 * Both [threshold] and [baselineKLDivergence] are computed during the call to [fit], the first one using [kFolds]-cross-validation, and
 * the second one as the KL-divergence between the data model and the process model, both computed only using the data passed to [fit].
 *
 * @param slack A multiplier for the maximal coefficient computed during fitting. The higher the value the lower the chance that an outlier will be recognized as a concept drift.
 * @param kFolds Number of folds in the cross validation to compute the threshold during fitting. If `<=0` then leaving-one-out is used instead.
 */
class KLDivergenceDriftDetector(
    val slack: Double = 1.1,
    val kFolds: Int = 3,
    var integrator: Integrator = MidpointIntegrator(0.001)
) :
    DriftDetector<Alignment, List<Alignment>> {

    companion object {
        private val logger = logger()
    }

    init {
        require(slack >= 1.0)
    }

    override var drift: Boolean = false
        private set
    private val features = ArrayList<Feature>()
    private val dataModels = ArrayList<KernelDensityEstimator>()
    private val processModels = ArrayList<KernelDensityEstimator>()
    private var baselineKLDivergence: Double = Double.NaN

    var threshold: Double = Double.NaN


    private fun driftCoefficient(baselineKLDivergence: Double, currentKLDivergence: Double) =
        (currentKLDivergence / (baselineKLDivergence + 1e-8) - 1.0).absoluteValue

    private fun cv(k: Int, process: List<List<Double>?>, data: List<List<Double>>): Double {
        require(process.size == data.size)
        return cvFolds(k, process.size).maxOf { testFold ->
            val processDistributions = process.allExcept(testFold).filterNotNull().transpose().map(List<Double>::toKDF)
            val dataDistributions = data.allExcept(testFold).transpose().map(List<Double>::toKDF)
            val baseline = NaiveKLDivergence(dataDistributions, processDistributions, integrator)
            testFold.maxOf {
                (processDistributions zip process[it].orEmpty()).forEach { (d, v) -> d.fit(listOf(v)) }
                (dataDistributions zip data[it]).forEach { (d, v) -> d.fit(listOf(v)) }
                val current = NaiveKLDivergence(dataDistributions, processDistributions, integrator)
                driftCoefficient(baseline, current)
            }
        }
    }

    override fun fit(artifact: List<Alignment>) {
        if (processModels.isNotEmpty())
            TODO("Incremental fitting is currently not supported, as CV must include previous knowledge")
        val propositional = artifact.map { a ->
            a.propositionalize().filterKeys { it.datatype == Double::class }.mapValues { it.value as Double }
        }
        features.addAll(propositional.flatMapTo(HashSet()) { it.keys })
        val fitting = ArrayList<List<Double>?>()
        val all = ArrayList<List<Double>>()
        for ((a, sparse) in artifact zip propositional) {
            fitting.add(if (a.cost == 0) features.map { sparse[it] ?: Double.NaN } else null)
            all.add(features.map { sparse[it] ?: Double.NaN })
        }
        // We assume there's no drift, i.e., shuffling is all right
        threshold = cv(if (kFolds <= 0) artifact.size else kFolds, fitting, all) * slack
        logger.debug { "threshold = $threshold" }

        processModels.addAll(fitting.filterNotNull().transpose().map(List<Double>::toKDF))
        dataModels.addAll(all.transpose().map(List<Double>::toKDF))

        baselineKLDivergence = NaiveKLDivergence(dataModels, processModels, integrator)
        drift = false
    }

    override fun observe(artifact: Alignment): Boolean {
        //TODO handle missing values for artifact
        artifact
            .propositionalize()
            .filterKeys { it.datatype == Double::class }
            .forEach { (f, v) ->
                val idx = features.indexOf(f)   //TODO efektywniejsze wyszukiwanie indeksu
                if (idx < 0)
                    TODO("A wild feature appears!")
                if (artifact.cost == 0)
                    processModels[idx].fit(listOf(v as Double))
                dataModels[idx].fit(listOf(v as Double))
            }
        drift =
            driftCoefficient(baselineKLDivergence, NaiveKLDivergence(dataModels, processModels, integrator)) > threshold
        return drift
    }

}