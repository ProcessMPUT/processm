package processm.miners.causalnet.onlineminer

import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.util.ArithmeticUtils

operator fun BigFraction.minus(other: BigFraction): BigFraction = this.subtract(other)
operator fun BigFraction.plus(other: BigFraction): BigFraction = this.add(other)
operator fun BigFraction.div(other: BigFraction): BigFraction = this.divide(other)
operator fun BigFraction.div(other: Int): BigFraction = this.divide(other)

/**
 * Computes the sum of inverses of the integers given in [values]
 *
 * For example, `sumOfReciprocals(listOf(2, 3)) == BigFraction(5, 6)`
 */
fun sumOfReciprocals(values: List<Int>): BigFraction {
    require(values.isNotEmpty())
    if (values.size == 1)
        return BigFraction(1, values.single())
    var n = 1L
    var d = values[0].toLong()
    for (i in 1 until values.size) {
        val x = values[i].toLong()
        val gcd = ArithmeticUtils.gcd(x, d)
        n = ((x / gcd) * n) + (d / gcd)
        check(n >= 0)
        check(n < Integer.MAX_VALUE)
        d = if (d > x)
            (d / gcd) * x
        else
            (x / gcd) * d
        check(d >= 0)
        check(d < Integer.MAX_VALUE)
        //assert(ArithmeticUtils.gcd(n,d)==1L)
    }
    return BigFraction(n.toInt(), d.toInt())
}