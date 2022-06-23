package processm.conformance.conceptdrift.numerical.integration

/**
 * An implementation of the Riemann's integral using [the trapezoidal rule](https://en.wikipedia.org/w/index.php?title=Riemann_sum&oldid=1089269404#Trapezoidal_rule)
 *
 * According to [Wikipedia](https://en.wikipedia.org/w/index.php?title=Riemann_sum&oldid=1089269404#Trapezoidal_rule) it may commit a larger error than [MidpointIntegrator]
 */
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