package processm.tools.helpers

import kotlin.math.ceil
import kotlin.math.ln

/**
 * Inverse cumulative distribution function for the geometric distribution such that its F(N)=theta (where F is the CDF)
 * P(X>=n)=(1-p)^n -> F(n) = 1-(1-p)^n
 *
 * Assuming constant n ([N]) and F(N) ([theta]), one can arrive at p=1-(1-F(N))^(1/N)
 * Assuming constant F(n) ([p]) and p, one can arrive at n=ln(1-F(n))/ln(1-p).
 * Combining the two, one arrives at the equation
 */
fun inverseGeometricCDF(p: Double, N: Int, theta: Double): Int = ceil(N * ln(1 - p) / ln(1 - theta)).toInt()