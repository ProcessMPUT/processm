package processm.conformance.conceptdrift.numerical

import kotlin.math.absoluteValue
import kotlin.math.pow

fun Double.isEffectivelyZero() = this.absoluteValue < 2.0.pow(-53)
