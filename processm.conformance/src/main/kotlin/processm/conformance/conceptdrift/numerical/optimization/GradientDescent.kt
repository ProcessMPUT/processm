package processm.conformance.conceptdrift.numerical.optimization

import processm.logging.logger

/**
 * A classic gradient descent algorithm, updating the argument by subtracting the current value of derivative multiplied by
 * [learningRate]
 */
class GradientDescent(
    var learningRate: Double = 0.01,
    var eps: Double = 1e-5
) : Optimizer, MultivariateOptimizer {

    companion object {
        val logger = logger()
    }

    override fun invoke(x0: Double, derivative: (Double) -> Double): Double =
        invoke(listOf(x0)) { listOf(derivative(it.single())) }.single()

    override fun invoke(x0: List<Double>, derivative: (List<Double>) -> List<Double>): List<Double> {
        val x = ArrayList(x0)
        val prevGrad = ArrayList(x0)
        val prevX = ArrayList(x0)
        var n = 0
        while (true) {
            val d = derivative(x).toMutableList()
            val norm = d.sumOf { it * it }
            if (norm < eps) {
                logger.debug("Gradient vanished, stopping")
                break
            }
            val th = 1.0 / learningRate
            val m = if (norm > th) th / norm else 1.0
            d.forEachIndexed { idx, v -> x[idx] -= learningRate * v * m }
            d.forEachIndexed { idx, v -> prevGrad[idx] = v }
            n++
        }
        return x
    }
}
