package processm.helpers


/**
 * Finds the lowest value v (from the range [low]..[high]) such that f(v) is not null.
 * Employs binary search and thus the function [f] is assumed to be a step function, i.e., there exists some value t
 * such that f(x) is null if, and only if, x<=t
 *
 * @param low The lower bound for search; if f(low) is not null, it is immediately returned
 * @param high The upper bound for search; f(high) must be non-null
 * @param eps The search resolution, i.e., the upper bound on how far the argument of [f] will lie from the true minimum
 * @param f The function to be minimized
 * @return The value of the function [f] for some x in t .. t+eps
 * @throws
 */
fun <T> stepArgMin(
    low: Double,
    high: Double,
    eps: Double = 1e-2,
    f: (v: Double) -> T?
): T {
    require(low.isFinite())
    require(high.isFinite())
    var l = low
    var h = high
    require(eps > 0.0)
    f(l)?.let { return it }
    var rh = requireNotNull(f(h))
    while (h - l > eps) {
        val v = (l + h) / 2
        val r = f(v)
        if (r === null) {
            l = v
        } else {
            h = v
            rh = r
        }
    }
    return rh
}
