package processm.conformance.rca.ml

import processm.core.models.commons.DecisionModel

data class LinearRegressionModel(val coefficients: List<Double>, val intercept: Double) :
    DecisionModel<List<Double>, Double> {
    override fun predict(x: List<Double>): Double {
        require(x.size == coefficients.size)
        return (x zip coefficients).sumOf { it.first * it.second } + intercept
    }

    val nFeatures: Int
        get() = coefficients.size
}