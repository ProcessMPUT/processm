package processm.enhancement.kpi.timeseries

/**
 * Computes the d-th degree difference of [x] in place, i.e., modifying [x], according to the following formula:
 *
 * ▽^d(x(t)) = ▽^(d-1)(x(t)) - ▽^(d-1)(x(t-1))
 * ▽^0(x(t)) = x(t)
 *
 * @return [x] without the last [d] elements
 */
internal fun computeDifferencesInPlace(x: MutableList<Double>, d: Int): MutableList<Double> {
    if (d == 0)
        return x
    require(d > 0)
    require(x.size > d)
    var n = x.size
    for (j in 1..d) {
        // per Eq 1.2.6 in [TSAFC]
        var previous = x[0]
        for (i in 1 until n) {
            x[i - 1] = x[i] - previous
            previous = x[i]
        }
        n--
    }
    return x.subList(0, n)
}