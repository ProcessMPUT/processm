package processm.miners.heuristicminer.windowing

import org.apache.commons.lang3.math.Fraction
import org.apache.commons.math3.util.ArithmeticUtils

operator fun Fraction.minus(other: Fraction) = this.subtract(other)
operator fun Fraction.plus(other: Fraction) = this.add(other)
operator fun Fraction.div(other: Fraction) = this.divideBy(other)
operator fun Fraction.div(other: Int) = this.divideBy(Fraction.getFraction(other, 1))

fun sumInverse(values: List<Int>): Fraction {
    require(values.isNotEmpty())
    if (values.size == 1)
        return Fraction.getFraction(1, values.single())
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
    return Fraction.getFraction(n.toInt(), d.toInt())
}