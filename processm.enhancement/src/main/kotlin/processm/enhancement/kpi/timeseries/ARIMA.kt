package processm.enhancement.kpi.timeseries

import processm.conformance.conceptdrift.numerical.optimization.GradientDescent
import processm.conformance.conceptdrift.numerical.optimization.MultivariateOptimizer
import processm.conformance.rca.ml.spark.SparkLinearRegression
import processm.core.models.commons.DecisionLearner
import kotlin.math.*


/**
 * A [DecisionLearner] implementing [ARIMA], i.e., a model for a timeseries including an auto-regressive component,
 * a differencing operator, and a moving average component (i.e., random shocks/noise from previous values).
 *
 * Bibliography
 *
 * [TSAFC]: George E. P. Box, Gwilym M. Jenkins, Gregory C. Reinsel, Greta M. Ljung: Time Series Analysis: Forecasting and Control, 5th Edition ISBN: 978-1-118-67502-1
 *
 * @param p The autoregressive order, i.e, the number of previous values in the timeseries to include
 * @param d The differentiation order
 * @param q The moving average order, i.e., the number of previous random shocks/noise values to include
 * @param optimizer Used for fitting models with [q]>0
 */
class ARIMA(
    val p: Int, val d: Int, val q: Int,
    var optimizer: MultivariateOptimizer = GradientDescent(0.001, eps = 1e-4)
) : DecisionLearner<List<Double>, List<Double>, Double> {

    constructor(params: ARIMAModelHyperparameters) : this(params.p, params.d, params.q) {}

    init {
        require(p >= 0) { "The number of lag observations must be non-negative" }
        require(d >= 0) { "The degree of differencing must be non-negative" }
        require(q >= 0) { "The moving-average order must be non-negative" }
    }


    private fun fitARMA(differences: List<Double>, start: List<Double>): ARIMAModel {
        assert(q > 0)
        val hasConstant =
            d == 0  // for d>0 c must be 0, otherwise the differences have non-zero mean, i.e., the mean of the differences of lower degrees depends on time, i.e, the process is non-stationary
        // Poor man's pseudo-random initialization. Lesson from artificial neural networks is that the initial values should be non-zero, different and small to avoid problems with gradient propagation
        val initial = (0 until p + q).mapTo(ArrayList()) { (if (it % 2 == 0) 1 else -1) * (it + 1) / 100.0 }
        if (hasConstant) initial.add(0.0)

        //I believe all the model parameters must be between -1 and 1, and a classic move in gradient optimization to achieve this is to use a sigmoid function. Here, tanh is used.
        //This seems to be oversimplification (c.f. [TSAFC] page 9), but I don't see how to implement it in a general manner
        fun f(x: Double): Double = 2 / (1 + exp(-x)) - 1
        var prevS = Double.POSITIVE_INFINITY
        val prevGrad = ArrayList(initial)
        var noise: List<Double> = emptyList()
        val optimized = optimizer.invoke(initial) { params ->
            val phi = params.subList(0, p).map(::f)
            val theta = params.subList(p, p + q).map(::f)
            val c = if (hasConstant) params[p + q] else 0.0
            val (w, a) = with(ExplicitTimeseries.backwardApproximation(differences, phi, theta, c)) {
                val n = differences.size + q
                w.takeLast(n) to a.takeLast(n)
            }
            noise = a
            val gradient = ArrayList<Double>(params.size)
            (1..p).mapTo(gradient) { i ->
                val agrad = ArrayList<Double>()
                a.indices.mapTo(agrad) { t ->
                    (if (t - i >= 0) -w[t - i] * (phi[i - 1] + 1) * (1 - phi[i - 1]) / 2 else 0.0) + (1..min(
                        q,
                        t
                    )).sumOf { j -> theta[j - 1] * agrad[t - j] }
                }
                2 * a.indices.sumOf { t -> a[t] * agrad[t] } / a.size
            }
            (1..q).mapTo(gradient) { i ->
                val agrad = ArrayList<Double>()
                a.indices.mapTo(agrad) { t ->
                    (if (t - i >= 0) (theta[i - 1] + 1) * (1 - theta[i - 1]) * a[t - i] / 2 else 0.0) + (1..min(
                        q,
                        t
                    )).sumOf { j -> theta[j - 1] * agrad[t - j] }
                }
                2 * a.indices.sumOf { t -> a[t] * agrad[t] } / a.size
            }
            if (hasConstant) gradient.add(run {
                val agrad = ArrayList<Double>()
                a.indices.mapTo(agrad) { t ->
                    -1 + (1..min(t, q)).sumOf { j -> theta[j - 1] * agrad[t - j] }
                }
                2 * a.indices.sumOf { t -> a[t] * agrad[t] } / a.size
            })

            val S = a.sumOf { it * it }
            //TODO in theory the following assert should be safe, as the optimized function is MSE, i.e, a convex function
            //I suspect two things:
            //1. There's an error in the code computing gradients.
            //2. There are multiple assumptions, and some values are unknown and are replaced by their expected values. Maybe this causes the problem to be non-convex?
//            assert(gradient.all(Double::isFinite) && (S <= prevS || (gradient zip prevGrad).any { (a, b) -> a.sign != b.sign || a.absoluteValue > b.absoluteValue }))
            prevS = S
            assert(gradient.size == params.size)
            assert(gradient.all(Double::isFinite))
            gradient.indices.forEach { prevGrad[it] = gradient[it] }
            return@invoke gradient
        }
        return ARIMAModel(
            optimized.subList(0, p).map(::f),
            optimized.subList(p, p + q).map(::f),
            if (hasConstant) optimized[p + q] else 0.0,
            start,
            noise
        )
    }


    private fun fitAR(data: List<Double>): ARIMAModel {
        val dataset = (p until data.size).map { i ->
            (1..p).map { j -> data[i - j] } to data[i]
        }
        val model = with(SparkLinearRegression()) {
            fitIntercept = (d == 0)
            fit(dataset)
        }
        assert(model.coefficients.size == p)
        return ARIMAModel(model.coefficients, emptyList(), model.intercept, emptyList(), emptyList())
    }

    /**
     * @param dataset Timeseries
     */
    override fun fit(dataset: List<Double>): ARIMAModel {
        val start = dataset.slice(0 until d)
        val differences = computeDifferencesInPlace(ArrayList(dataset), d)
        return if (q == 0) {
            if (p > 0) fitAR(differences)
            else ARIMAModel(emptyList(), emptyList(), if (d == 0) differences.average() else 0.0, start, emptyList())
        } else {
            fitARMA(differences, start)
        }
    }
}