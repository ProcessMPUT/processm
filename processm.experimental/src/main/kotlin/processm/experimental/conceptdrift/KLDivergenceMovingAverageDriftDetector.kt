package processm.experimental.conceptdrift

import org.apache.commons.collections4.queue.CircularFifoQueue
import org.apache.commons.math3.stat.inference.TTest
import processm.conformance.conceptdrift.estimators.KernelDensityEstimator
import processm.conformance.conceptdrift.numerical.integration.Integrator
import processm.conformance.conceptdrift.numerical.integration.MidpointIntegrator
import processm.conformance.conceptdrift.statisticaldistance.NaiveKLDivergence
import processm.conformance.models.alignments.Alignment
import processm.conformance.rca.Feature
import processm.conformance.rca.propositionalize
import processm.core.logging.debug
import processm.core.logging.logger
import java.util.*

private fun List<Double>.toKDF(): KernelDensityEstimator {
    //TODO handle NaNs
    val kdf = KernelDensityEstimator()
    kdf.fit(this)
    return kdf
}

/**
 * An attempt to do moving average detection on a history of KL-divergences. Doesn't seem to work.
 */
internal class KLDivergenceMovingAverageDriftDetector(val windowSize: Int) : DriftDetector<Alignment, List<Alignment>> {
    init {
        require(windowSize > 0)
    }

    override var drift: Boolean = false
        private set
    private val features = ArrayList<Feature>()
    private val dataModels = ArrayList<KernelDensityEstimator>()
    private val processModels = ArrayList<KernelDensityEstimator>()

    var integrator: Integrator = MidpointIntegrator(0.001)

    private val history = CircularFifoQueue<Double>(2 * windowSize)

    val currentKLDivergence: Double
        get() {
            return NaiveKLDivergence(
                dataModels,
                processModels,
                integrator
            )
        }

    companion object {

        private val logger = logger()

    }

    private fun hasDrift(): Boolean {
        if (!history.isAtFullCapacity)
            return false
        val before = (0 until windowSize).map(history::get)
        val after = (windowSize until 2 * windowSize).map(history::get)
        val pvalue = TTest().tTest(before.toDoubleArray(), after.toDoubleArray())
        println("pvalue=$pvalue before~${before.average()} after~${after.average()}")
        return pvalue < 0.05
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

        val n = fitting.size
        processModels.addAll(
            fitting.subList(0, n - 2 * windowSize).filterNotNull().transpose().map(List<Double>::toKDF)
        )
        dataModels.addAll(all.subList(0, n - 2 * windowSize).transpose().map(List<Double>::toKDF))

        for ((prow, drow) in fitting.subList(n - 2 * windowSize, n) zip all.subList(n - 2 * windowSize, n)) {
            if (prow !== null)
                (prow zip processModels).forEach { (v, d) -> d.fit(listOf(v)) }
            (drow zip dataModels).forEach { (v, d) -> d.fit(listOf(v)) }
            history.add(currentKLDivergence)
        }
        println(history)
        drift = false
    }

    override fun observe(artifact: Alignment): Boolean {
        //TODO handle missing values for artifact
        artifact
            .propositionalize()
            .filterKeys { it.datatype == Double::class }
            .forEach { (f, v) ->
                val idx = features.indexOf(f)   //TODO efektywniejsze wyszukiwanie indeksu
                if (idx < 0) {
                    logger.debug { "features=$features" }
                    logger.debug { "f=$f" }
                    TODO("A wild feature appears!")
                }
                if (artifact.cost == 0)
                    processModels[idx].fit(listOf(v as Double))
                dataModels[idx].fit(listOf(v as Double))
            }
        history.add(currentKLDivergence)
        drift = hasDrift()
        return drift
    }
}