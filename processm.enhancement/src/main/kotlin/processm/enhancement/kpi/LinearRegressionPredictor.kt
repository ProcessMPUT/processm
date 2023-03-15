package processm.enhancement.kpi

import processm.conformance.rca.ml.LinearRegressionModel
import processm.conformance.rca.ml.spark.SparkLinearRegression
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.stats.Distribution
import processm.core.log.Event
import processm.core.log.hierarchical.Log
import java.util.HashMap

/**
 * [Predictor] based on a [SparkLinearRegression]. A separate model is built for each pair `(eventKey, attributeKey)`, where
 * `eventKey` is the output of [classifier], and `attributeKey` is the KPI. The model is supposed to predict the value
 * of the KPI `attributeKey` for the event identified by `eventKey`, based on the last `n` values of the KPI in the current trace.
 * `n` is computed during training as `median-1.5*IQR`, where `median` is the median length of sequences of events with the KPI
 * preceding the event with the same `eventKey` in the training set, and IQR is the interquartile range of the same lengths.
 * The employed formula is one of few typically used to detect outliers.
 *
 * The intuition is simple: given the values of the KPI in the previous events in the same trace, predict the value for the current event.
 */
class LinearRegressionPredictor(val classifier: (Event) -> Any? = { it.conceptName }) : Predictor {

    private val models = DoublingMap2D<Any?, String, LinearRegressionModel>()

    override fun fit(logs: Sequence<Log>) {
        val datasets = DoublingMap2D<Any?, String, ArrayList<Pair<List<Double>, Double>>>()
        for (log in logs) {
            for (trace in log.traces) {
                val history = HashMap<String, ArrayList<Double>>()
                for (event in trace.events) {
                    val eventKey = classifier(event)
                    for ((key, attribute) in event.attributes)
                        if (attribute is Number) {
                            val current = attribute.toDouble()
                            val previous = history[key]
                            if (!previous.isNullOrEmpty())
                                datasets
                                    .compute(eventKey, key) { _, _, old -> old ?: ArrayList() }
                                    ?.add(ArrayList(previous) to current)
                            history.computeIfAbsent(key) { ArrayList() }.add(current)
                        }
                }
            }
        }
        models.clear()
        models.putAll(datasets.mapValues { _, _, dataset ->
            val lengths = Distribution(dataset.map { (x, _) -> x.size.toDouble() })
            val nFeatures = (lengths.median - 1.5 * (lengths.Q3 - lengths.Q1)).toInt().coerceAtMost(lengths.count)
                .coerceAtLeast(lengths.min.toInt())

            SparkLinearRegression().fit(dataset.mapNotNull { (x, y) ->
                if (x.size >= nFeatures)
                    x.takeLast(nFeatures) to y
                else
                    null
            })
        })
    }

    private val currentTrace = HashMap<String, ArrayList<Double>>()

    override fun startTrace() {
        currentTrace.clear()
    }

    override fun observeEvent(event: Event) {
        for ((key, attribute) in event.attributes)
            if (attribute is Number)
                currentTrace.computeIfAbsent(key) { ArrayList() }.add(attribute.toDouble())
    }

    override fun predict(ongoingEvent: Event): Map<String, Double> {
        val result = HashMap<String, Double>()
        for ((key, model) in models.getRow(classifier(ongoingEvent))) {
            val history = currentTrace[key].orEmpty()
            if (model.nFeatures <= history.size)
                result[key] = model.predict(history.takeLast(model.nFeatures))
        }
        return result
    }

}