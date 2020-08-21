package processm.miners.heuristicminer.windowing

import org.apache.commons.lang3.math.Fraction

operator fun Fraction.minus(other: Fraction) = this.subtract(other)
operator fun Fraction.plus(other: Fraction) = this.add(other)
operator fun Fraction.div(other: Fraction) = this.divideBy(other)
operator fun Fraction.div(other: Int) = this.divideBy(Fraction.getFraction(other, 1))
