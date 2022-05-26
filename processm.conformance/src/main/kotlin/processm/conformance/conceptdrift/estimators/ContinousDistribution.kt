package processm.conformance.conceptdrift.estimators

interface ContinousDistribution {

//    val mean: Double
//    val stdev: Double

    val lowerBound: Double
    val upperBound: Double


//    fun fit(data: Iterable<Double>)
    fun pdf(x: Double): Double
//    fun cdf(x: Double): Double


}