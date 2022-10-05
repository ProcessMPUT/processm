package processm.enhancement.kpi.timeseries

import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/**
 * An approximation of a timeseries. See Chapter 6.4.3 in [TSAFC]
 *
 * @param w The timeseries itself
 * @param a The random shocks (noise) in the forward model 𝜙(𝐵)𝑤𝑡 = 𝜃(𝐵)𝑎𝑡
 * @param e The random shocks (noise) in the backward model 𝜙(𝐹)𝑤𝑡 = 𝜃(𝐹)𝑒𝑡
 */
internal data class ExplicitTimeseries(val w: List<Double>, val a: List<Double>, val e: List<Double>) {

    companion object {

        /**
         * A representation of an MA process, primarily intended as a finite approximation of an arbitrary ARMA process
         *
         * z_t = \sum_{j=0}^q psi_j a_{t-j} + c
         *
         * Always psi[0] = 1
         */
        private data class MA(val psi: List<Double>, val c: Double)

        /**
         * Approximates an ARMA process defined by ([phi], [theta], [c]) with a (finite) MA process, cutting off at the
         * first coefficient with the absolute value `<=` [eps]
         */
        private fun arma2ma(
            phi: List<Double>,
            theta: List<Double>,
            c: Double,
            eps: Double = 1e-5,
            maxQ: Int = Integer.MAX_VALUE
        ): MA {
            val p = phi.size
            val psi = ArrayList<Double>()
            // per Section 3.4.1
            psi.add(1.0)
            while (psi.size < maxQ) {
                val j = psi.size
                val coefficient =
                    (1..min(p, j)).sumOf { phi[it - 1] * psi[j - it] } - (if (j - 1 < theta.size) theta[j - 1] else 0.0)
                if (coefficient.absoluteValue <= eps)
                    break
                psi.add(coefficient)
            }
            // Own result:
            // 1. z_t = a_t - \sum_{j=1}^\infty \psi_j\a_{t-j} + \alpha c
            // 2. z_t = \sum_{j=1}^p z_{t-j} + a_t - \sum_{j=1}^q \phi_ja_{t-j} + c
            // Now substitute z_{t-j} in 2 with 1 and solve for \alpha
            val newC = c / (1 - phi.sum())
            return MA(psi, newC)
        }

        /**
         * Given a timeseries [data] and an ARMA coefficients ([phi], [theta], [c]), computes an explicit representation
         * of the timeseries incl. random forward and backward random shocks using the approximation given by Equations
         * 7.1.7 and 7.1.8 in [TSAFC]
         */
        fun backwardApproximation(
            data: List<Double>,
            phi: List<Double>,
            theta: List<Double>,
            c: Double
        ): ExplicitTimeseries {

            val (psi, c_) = arma2ma(phi, theta, c, maxQ = data.size)

            val q = psi.size
            val n = data.size
            val start = -psi.size
            val w = DoubleArray(n - start) { if (it + start >= 0) data[it + start] else 0.0 }
            val e = DoubleArray(n - start) { 0.0 }
            val a = DoubleArray(n - start) { 0.0 }
            for (t in n - start - 1 downTo -start) {
                e[t] = w[t] - c_ - (max(-t, 1) until min(q, n - start - t)).sumOf { psi[it] * e[it + t] }
            }
            for (t in -start - 1 downTo 0) {
                w[t] = c_ + (0 until min(q, e.size - t)).sumOf { psi[it] * e[t + it] }
            }
            for (t in a.indices) {
                a[t] = w[t] - c_ - (1 until min(q, t + 1)).sumOf { psi[it] * a[t - it] }
            }
            return ExplicitTimeseries(w.toList(), a.toList(), e.toList())
        }
    }
}