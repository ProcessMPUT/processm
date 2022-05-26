package processm.conformance.conceptdrift.estimators

interface DiscreteDistribution {
    val support: Set<Any>
    fun fit(data: Iterable<Any>)
    fun logP(x: Any): Double
}