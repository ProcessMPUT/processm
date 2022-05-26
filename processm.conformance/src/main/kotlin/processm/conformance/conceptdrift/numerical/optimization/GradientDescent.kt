package processm.conformance.conceptdrift.numerical.optimization

import kotlin.math.absoluteValue

class GradientDescent(var learningRate: Double = 0.01, var eps: Double = 1e-5) : Optimizer {
    override fun invoke(x0: Double, derivative: (Double) -> Double): Double {
        var x = x0
        while (true) {
            val d = derivative(x)
            if (d.absoluteValue < eps)
                break
            x -= learningRate * d
        }
        return x
    }
}