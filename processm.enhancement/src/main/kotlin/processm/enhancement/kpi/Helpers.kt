package processm.enhancement.kpi

import processm.core.log.attribute.Attribute
import processm.core.log.attribute.IntAttr
import processm.core.log.attribute.RealAttr
import processm.core.log.attribute.value


internal fun Attribute<*>.isNumeric(): Boolean = this is IntAttr || this is RealAttr
internal fun Attribute<*>.toDouble(): Double = (this.value as Number).toDouble()