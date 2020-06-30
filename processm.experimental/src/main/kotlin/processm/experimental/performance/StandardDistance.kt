package processm.experimental.performance

import processm.core.log.Event
import processm.core.models.commons.Activity

class StandardDistance:Distance {
    override val maxAcceptableDistance = Double.POSITIVE_INFINITY

    override fun invoke(a: Activity?, e: Event?): Double {
        require(a !== null || e !== null) { "An illegal move. At least one of the two arguments must be non-null." }
        return if (a === null || e === null)
            1.0
        else if (a.name == e.conceptName)
            0.0
        else
            Double.POSITIVE_INFINITY
    }
}