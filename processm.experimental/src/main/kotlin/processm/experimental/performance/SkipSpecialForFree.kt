package processm.experimental.performance

import processm.core.log.Event
import processm.core.models.causalnet.Node
import processm.core.models.commons.Activity

open class SkipSpecialForFree(val base: Distance) : Distance {
    override val maxAcceptableDistance: Double
        get() = base.maxAcceptableDistance

    override fun invoke(a: Activity?, e: Event?): Double =
        if (e == null && a is Node && a.special)
            0.0
        else
            base(a, e)
}