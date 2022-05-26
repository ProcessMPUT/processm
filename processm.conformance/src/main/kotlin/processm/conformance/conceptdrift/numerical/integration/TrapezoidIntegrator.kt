package processm.conformance.conceptdrift.numerical.integration

class TrapezoidIntegrator(override val step: Double) : Integrator {
    override fun invoke(lower: Double, upper: Double, f: (Double) -> Double): Double {
        var x = lower
        var result = 0.0
        while (x < upper) {
            result += (f(x) + f(x + step)) / 2 * step
            x += step
        }
        return result
    }
}