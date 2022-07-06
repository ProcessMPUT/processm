package processm.conformance.conceptdrift

import processm.conformance.conceptdrift.estimators.ContinuousDistribution
import processm.conformance.conceptdrift.estimators.KernelDensityEstimator
import processm.conformance.conceptdrift.estimators.computeKernelDensityEstimator
import processm.conformance.conceptdrift.numerical.integration.Integrator
import processm.conformance.conceptdrift.numerical.integration.MidpointIntegrator
import processm.conformance.models.alignments.Alignment
import processm.conformance.rca.Feature
import processm.conformance.rca.propositionalize
import processm.core.logging.debug
import processm.core.logging.logger
import java.util.*
import kotlin.random.Random


/**
 * Every [observe]d [Alignment] is used to update the data model. These [Alignment]s that have [Alignment.cost]=0, are
 * also used to update the process model. If, at any time, `abs(KLDivergence/baselineKLDivergence - 1)>threshold`, a concept drift is reported.
 * Both [threshold] and [baselineKLDivergence] are computed during the call to [fit], the first one using [kFolds]-cross-validation, and
 * the second one as the KL-divergence between the data model and the process model, both computed only using the data passed to [fit].
 *
 * @param slack A multiplier for the maximal coefficient computed during fitting. The higher the value the lower the chance that an outlier will be recognized as a concept drift.
 * @param kFolds Number of folds in the cross validation to compute the threshold during fitting. If `<=0` then leaving-one-out is used instead.
 */
open class BoundStatisticalDistanceDriftDetector(
    val distance: (List<ContinuousDistribution>, List<ContinuousDistribution>, Integrator) -> Double,
    val slack: Double = 1.5,
    val kFolds: Int = 10,
    var integrator: Integrator = MidpointIntegrator(0.001),
    val newFeatureIsDrift: Boolean = true,
    val rng: Random = Random(0xbeef900d)
) : DriftDetector<Alignment, List<Alignment>> {

    companion object {
        private val logger = logger()
    }

    init {
        require(slack >= 1.0)
    }

    override var drift: Boolean = false
        protected set
    protected val features = ArrayList<Feature>()
    protected val featureToIndex = HashMap<Feature, Int>()
    protected val dataModels = ArrayList<KernelDensityEstimator>()
    protected val processModels = ArrayList<KernelDensityEstimator>()

    var threshold: Double = Double.NaN


    private fun cv(k: Int, process_: List<List<Double>?>, data_: List<List<Double>>): Double {
        val shuffled = process_.indices.toList().shuffled(rng)
        val process = shuffled.map { process_[it] }
        val data = shuffled.map { data_[it] }
        require(process.size == data.size)
        return cvFolds(k, process.size).maxOf { testFold ->
            val processDistributions = process.allExcept(testFold).filterNotNull().transpose().map(List<Double>::computeKernelDensityEstimator)
            val dataDistributions = data.allExcept(testFold).transpose().map(List<Double>::computeKernelDensityEstimator)
            val (fitting, notFitting) = testFold.partition { process[it] !== null }
            fitting.forEach {
                (processDistributions zip process[it]!!).forEach { (d, v) -> d.fit(listOf(v)) }
                (dataDistributions zip data[it]).forEach { (d, v) -> d.fit(listOf(v)) }
            }
            notFitting.forEach {
                (dataDistributions zip data[it]).forEach { (d, v) -> d.fit(listOf(v)) }
            }
            //This is a heuristic, as there's no guarantee that this is the maximal value. Unfortunately computing the distance for every point where process[it]==null is too expensive
            distance(dataDistributions, processDistributions, integrator)
        }
    }

    override fun fit(artifact: List<Alignment>) {
        if (processModels.isNotEmpty())
            throw UnsupportedOperationException("Incremental fitting is not supported")
        val propositional = artifact.map { a ->
            a.propositionalize().filterKeys { it.datatype == Double::class }.mapValues { it.value as Double }
        }
        features.clear()
        featureToIndex.clear()
        features.addAll(propositional.flatMapTo(HashSet()) { it.keys })
        features.forEachIndexed { idx, f -> featureToIndex[f] = idx }
        val fitting = ArrayList<List<Double>?>()
        val all = ArrayList<List<Double>>()
        for ((a, sparse) in artifact zip propositional) {
            fitting.add(if (a.cost == 0) features.map { sparse[it] ?: Double.NaN } else null)
            all.add(features.map { sparse[it] ?: Double.NaN })
        }
        // We assume there's no drift, i.e., shuffling is all right
        threshold = cv(if (kFolds <= 0) artifact.size else kFolds, fitting, all) * slack
        logger.debug { "threshold = $threshold" }
        check(threshold.isFinite())

        processModels.addAll(fitting.filterNotNull().transpose().map(List<Double>::computeKernelDensityEstimator))
        dataModels.addAll(all.transpose().map(List<Double>::computeKernelDensityEstimator))

        drift = false
    }

    override fun observe(artifact: Alignment): Boolean {
        artifact
            .propositionalize()
            .filterKeys { it.datatype == Double::class }
            .forEach { (f, v) ->
                val idx = featureToIndex[f]
                if (idx != null) {
                    if (artifact.cost == 0)
                        processModels[idx].fit(listOf(v as Double))
                    dataModels[idx].fit(listOf(v as Double))
                } else {
                    if (newFeatureIsDrift) {
                        logger.info("An unknown feature $f appeared. This constitutes a drift.")
                        drift = true
                    }
                    logger.warn("Ignoring a new feature $f")
                }
            }
        if ((drift && artifact.cost == 0) || (!drift && artifact.cost != 0)) {
            val d = distance(dataModels, processModels, integrator)
            val newDrift = d > threshold
            if (newDrift != drift) {
                logger.debug { "Drift status changed to $newDrift. d=$d threshold=$threshold" }
                drift = newDrift
            }
        }
        return drift
    }

}