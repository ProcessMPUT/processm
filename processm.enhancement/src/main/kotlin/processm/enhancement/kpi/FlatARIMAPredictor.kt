package processm.enhancement.kpi

import processm.core.log.Event
import processm.core.log.hierarchical.Log
import processm.enhancement.kpi.timeseries.ARIMA
import processm.enhancement.kpi.timeseries.ARIMAModel
import processm.enhancement.kpi.timeseries.identifyARIMAModel
import java.util.*
import kotlin.collections.ArrayList

/**
 * Maintains a separate [ARIMAModel] for each KPI.
 */
class FlatARIMAPredictor : Predictor {

    private val modelsInternal = HashMap<String, ARIMAModel>()
    private val kpis = HashMap<String, ArrayList<Double>>()

    val models: Map<String, ARIMAModel> = Collections.unmodifiableMap(modelsInternal)

    /**
     * Events are considered as a single sequence, without distinguished traces or logs.
     * Each KPI corresponds to a separate timeseries that is modelled using [ARIMA].
     */
    override fun fit(logs: Sequence<Log>) {
        for (log in logs) {
            for (trace in log.traces) {
                for (event in trace.events)
                    for ((key, attribute) in event.attributes)
                        if (attribute.isNumeric())
                            kpis.computeIfAbsent(key) { ArrayList() }.add(attribute.toDouble())
            }
        }
        kpis.mapValuesTo(modelsInternal) { (_, values) ->
            val hyperparameters = identifyARIMAModel(values)
            ARIMA(hyperparameters).fit(values)
        }
    }

    override fun startTrace() {
    }

    override fun observeEvent(event: Event) {
        for ((key, attribute) in event.attributes)
            if (attribute.isNumeric())
                kpis[key]?.add(attribute.toDouble())
    }

    override fun predict(ongoingEvent: Event): Map<String, Double> {
        return modelsInternal.mapValues { (attribute, model) -> model.predict(kpis[attribute]!!) }
    }
}