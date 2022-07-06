package processm.conformance.conceptdrift.numerical.integration

interface Integrator {
    val step: Double

    operator fun invoke(lower: Double, upper: Double, f: (Double) -> Double): Double

    operator fun invoke(ranges: List<ClosedFloatingPointRange<Double>>, f: (Double) -> Double): Double =
        ranges.sumOf { this(it.start, it.endInclusive, f) }
}