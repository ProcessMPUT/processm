package processm.conformance.conceptdrift.numerical.optimization

fun interface Optimizer {
    operator fun invoke(x0: Double, derivative: (Double) -> Double): Double
}