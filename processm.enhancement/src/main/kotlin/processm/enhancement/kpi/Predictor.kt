package processm.enhancement.kpi

import processm.core.log.Event
import processm.core.log.hierarchical.Log

/**
 * A predictor for KPIs. The general workflow is as follows: first, one calls [fit] to train the predictor, providing a
 * set of completed business cases. This populates the predictor with necessary historical information. Then, to use it,
 * in the beginning on an ongoing business case, one calls [startTrace]. Then, for every ongoing [Event], one calls
 * [predict] to predict its KPIs. Once the [Event] is completed, it is provided to the [Predictor] by calling [observeEvent].
 */
interface Predictor {
    /**
     * Trains the predictor on historical logs
     */
    fun fit(logs: Sequence<Log>)

    /**
     * To be called when a new trace starts
     */
    fun startTrace()

    /**
     * Called when an event has been completed and its KPIs are known
     */
    fun observeEvent(event: Event)

    /**
     * Called on an ongoing event to predict KPIs
     *
     * @return A mapping from a KPI (attribute) name to a predicted value
     */
    fun predict(ongoingEvent: Event): Map<String, Double>
}

