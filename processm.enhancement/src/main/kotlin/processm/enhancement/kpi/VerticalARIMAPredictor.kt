package processm.enhancement.kpi

import processm.core.log.Event
import processm.core.log.hierarchical.Log
import processm.enhancement.kpi.timeseries.ARIMA
import processm.enhancement.kpi.timeseries.ARIMAModel
import processm.enhancement.kpi.timeseries.identifyARIMAModel
import processm.helpers.map2d.DoublingMap2D

/**
 * [VerticalARIMAPredictor] assumes that events with the same value of [classifier] form (for each KPI separately) a timeseries
 * that can be modelled using [ARIMA]. It assumes that the value of an KPI depends on the previous values of the KPI for
 * the corresponding events in the previous traces rather than on the values of the KPI for events directly preceding.
 * It follows from an intuition that consecutive traces are similar.
 */
class VerticalARIMAPredictor(val classifier: (Event) -> Any? = { it.conceptName }) : Predictor {

    private val kpis = DoublingMap2D<Any?, String, ArrayList<Double>>()
    private val models = DoublingMap2D<Any?, String, ARIMAModel>()

    var minDatasetSize: Int = 10

    override fun fit(logs: Sequence<Log>) {
        for (log in logs)
            for (trace in log.traces) {
                startTrace()
                for (event in trace.events) {
                    val eventKey = classifier(event)
                    for ((key, attribute) in event.attributes)
                        if (attribute is Number)
                            kpis.compute(eventKey, key) { _, _, old -> old ?: ArrayList() }?.add(attribute.toDouble())
                }
            }
        models.clear()
        models.putAll(kpis.mapValuesNotNull { _, _, timeseries ->
            if (timeseries.size >= minDatasetSize)
                ARIMA(identifyARIMAModel(timeseries)).fit(timeseries)
            else
                null
        })
    }

    override fun startTrace() {
    }

    override fun observeEvent(event: Event) {
        val eventKey = classifier(event)
        for ((key, attribute) in event.attributes)
            if (attribute is Number)
                kpis[eventKey, key]?.add(attribute.toDouble())
    }

    override fun predict(ongoingEvent: Event): Map<String, Double> {
        val eventKey = classifier(ongoingEvent)
        return models.getRow(eventKey)
            .mapValues { (attributeKey, model) -> model.predict(kpis[eventKey, attributeKey]!!) }
    }

}
