package processm.conformance.conceptdrift.numerical.optimization

fun interface MultivariateOptimizer {
    /**
     * Let [derivative] be the derivative of some function `f`. [invoke] returns a value `x` such that `derivative(x)`
     * is approximately 0, i.e., `x` is a local optimum for `f`.
     *
     * @param x0 A starting point for the optimization process. Different starting points can yield different results
     * for the same [derivative]
     */
    operator fun invoke(x0: List<Double>, derivative: (List<Double>) -> List<Double>): List<Double>
}