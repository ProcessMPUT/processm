package processm.conformance.conceptdrift.numerical.integration

/**
 * An implementation of [the midpoint rule](https://en.wikipedia.org/w/index.php?title=Numerical_integration&oldid=1086230517#Quadrature_rules_based_on_interpolating_functions)
 * with a fixed step size
 *
 * I've tried to use [org.apache.commons.math3.analysis.integration.MidPointIntegrator], but it was slow and/or yielding
 * weird results, like the integral being above 2, when the expected value was <= 1
 */
class MidpointIntegrator(override val step: Double) : Integrator {
    override fun invoke(lower: Double, upper: Double, f: (Double) -> Double): Double {
        require(lower.isFinite()) {"The lower bound must be finite. Currently it is $lower"}
        require(upper.isFinite()) {"The upper bound must be finite. Currently it is $upper"}
        require(lower <= upper)
        var x = lower
        var result = 0.0
        while (x < upper) {
            result += f(x + step / 2)
            x += step
        }
        return result * step
    }
}