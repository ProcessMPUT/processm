package processm.experimental.performance

import processm.core.log.Event
import processm.core.models.commons.Activity

interface Distance {
    val maxAcceptableDistance: Double

    operator fun invoke(a: Activity?, e: Event?): Double
}