package processm.conformance.conceptdrift.numerical.optimization

import processm.conformance.conceptdrift.numerical.isEffectivelyZero
import kotlin.math.absoluteValue
import kotlin.math.sqrt


/**
 * RMSProp as described in https://en.wikipedia.org/w/index.php?title=Stochastic_gradient_descent&oldid=1087978987#RMSProp
 * Default values of [learningRate] and [forgettingFactor] as in PyTorch 1.11.0
 *
 * [dampeningFactor] and [maxFluctuations] are a novelty, used to divide the learning rate if the sign of the derivative
 * keeps fluctuating between positive and negative.
 */
class RMSProp(
    var learningRate: Double = 0.01,
    var forgettingFactor: Double = 0.99,
    var eps: Double = 1e-5,
    val dampeningFactor: Double = 10.0,
    val maxFluctuations: Int = 5
) : Optimizer {

    override fun invoke(x0: Double, derivative: (Double) -> Double): Double {
        var x = x0
        var v = 0.0
        var prevd = 0.0
        var nFluctuations = 0
        while (true) {
            val d = derivative(x)
            if (d * prevd < 0) {
                nFluctuations++
                if (nFluctuations >= maxFluctuations) {
                    learningRate /= dampeningFactor
                    nFluctuations = 0
                }
            } else
                nFluctuations = 0
            prevd = d
            if (d.absoluteValue < eps)
                break
            v = forgettingFactor * v + (1 - forgettingFactor) * d * d
            val update = (learningRate / sqrt(v)) * d
            if (update.isEffectivelyZero())
                break
            x -= update
        }
        return x
    }
}