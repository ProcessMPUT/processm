package processm.enhancement.kpi.timeseries

import processm.core.models.commons.DecisionModel
import kotlin.math.min

/**
 * A fitted (trained) ARIMA process. Let z denote the differentiated timeseries, the paramters [phi], [theta] and [c] are
 * the interpreted according to the formula:
 * z_t = \sum_{i=1}^n phi_i * (z_{t-i}-c) + \sum_{i=1}^n theta_i * a_{t-i} + a_t + c
 *
 * Where a_t is the white noise process/random shocks. They are assumed to be i.i.d., see Chapter 1.2.1 in [1]
 *
 * @param initial The starting values of the original timeseries, its length is equal to the differencing order of the process
 * @param noise The white noise
 */
data class ARIMAModel(
    val phi: List<Double>,
    val theta: List<Double>,
    val c: Double,
    val initial: List<Double>,
    val noise: List<Double>
) : DecisionModel<List<Double>, Double> {

    init {
        require(c == 0.0 || d == 0) { "Either the degree of differencing (d=$d) must be 0 or the constant (c=$c) must be 0, otherwise the mean of differences changes over time" }
    }

    /**
     * The autoregressive order
     */
    val p: Int
        get() = phi.size

    /**
     * The moving average order
     */
    val q: Int
        get() = theta.size

    /**
     * The differencing order
     */
    val d: Int
        get() = initial.size

    /**
     * @param row The previous values of the timeseries
     */
    override fun predict(row: List<Double>): Double {
        val differences = computeDifferences(row)
        val z = differences.map { it.last() }.reversed()
        val a = ArrayList<Double>()
        z.indices.mapTo(a) { t ->
            z[t] - (1..min(p, t)).sumOf { j -> phi[j - 1] * z[t - j] } +
                    (1..min(q, t)).sumOf { j -> theta[j - 1] * a[t - j] } - c
        }
        return simulate(differences) { if (it in a.indices) a[it] else 0.0 }.drop(row.size).take(1).single()
    }

    /**
     * Simulates the process starting from the initial values [initial] and using [noise] as the source of random shocks a_t
     */
    fun simulate(noise: (Int) -> Double = { if (it in this.noise.indices) this.noise[it] else 0.0 }) =
        simulate(initial, noise)


    /**
     * Simulates the process starting from the given [history] and using [noise] as the source of random shocks a_t
     */
    fun simulate(history: List<Double>, noise: (Int) -> Double) = simulate(computeDifferences(history), noise)

    private fun computeDifferences(history: List<Double>): ArrayDeque<DoubleArray> {
        require(history.size >= d)
        //outer - time (i-th element is differences starting from t-i), inner - difference order (0-th element is no differencing, i.e., the time series the user is actually interested in)
        val differences = ArrayDeque<DoubleArray>()
        for (v in history.reversed())
            differences.addLast(DoubleArray(d + 1).also { it[0] = v })
        val ez = c / (1 - theta.sum())
        for (j in 1..d) {
            for (i in 0 until differences.size - 1)
                differences[i][j] = differences[i][j - 1] - differences[i + 1][j - 1]
            differences[differences.size - 1][j] = differences[differences.size - 1][j - 1] - (if (j == d) ez else 0.0)
        }
        return differences
    }

    private fun simulate(differences: ArrayDeque<DoubleArray>, noise: (Int) -> Double) = sequence {
        val z = ArrayDeque<Double>().also { z ->
            for (i in 0 until p.coerceAtMost(differences.size))
                z.add(differences[i][d])
            val cForZ = if (d == 0) c else 0.0
            for (i in p.coerceAtMost(differences.size) until p)
                z.add(cForZ)
        }
        val previous = if (d > 0) differences[0] else doubleArrayOf()
        val current = DoubleArray(d + 1)
        for (row in differences.reversed())
            yield(row[0])
        var step = differences.size - theta.size
        val a = ArrayDeque<Double>().also { a ->
            theta.forEach { _ -> a.addFirst(noise(step++)) }
        }
        while (true) {
            val at = noise(step++)
            val zt =
                (phi zip z).sumOf { (phij, zj) -> phij * zj } - (theta zip a).sumOf { (thetaj, aj) -> thetaj * aj } + at + c
            if (z.isNotEmpty()) {
                z.removeLast()
                z.addFirst(zt)
            }
            if (a.isNotEmpty()) {
                a.removeLast()
                a.addFirst(at)
            }
            val prediction = if (d > 0) {
                current[d] = zt
                for (j in d - 1 downTo 0)
                    current[j] = current[j + 1] + previous[j]
                System.arraycopy(current, 0, previous, 0, current.size)
                current[0]
            } else zt
            yield(prediction)
        }
    }

    /**
     * Computes residuals/noise/shocks a_t for the given timeseries
     */
    fun computeResiduals(timeseries: List<Double>): List<Double> {
        val differences = computeDifferencesInPlace(ArrayList(timeseries), d)
        val a = ExplicitTimeseries.backwardApproximation(differences, phi, theta, c).a
        val result = a.slice(a.size - differences.size until a.size)
        assert(result.size == differences.size)
        return result
    }
}