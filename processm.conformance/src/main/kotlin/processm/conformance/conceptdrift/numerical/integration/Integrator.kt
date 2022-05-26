package processm.conformance.conceptdrift.numerical.integration

interface Integrator {
    val step: Double

    operator fun invoke(lower: Double, upper: Double, f: (Double) -> Double): Double
}