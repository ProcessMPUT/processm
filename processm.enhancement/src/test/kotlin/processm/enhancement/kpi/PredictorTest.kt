package processm.enhancement.kpi

import processm.core.DBTestHelper
import processm.core.helpers.Counter
import processm.core.log.Helpers.assertDoubleEquals
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.querylanguage.Query
import java.util.*
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

class PredictorTest {
    companion object {
        private val dbName = DBTestHelper.dbName
        private val logUUID: UUID = DBTestHelper.JournalReviewExtra
    }

    private fun q(pql: String): Log =
        DBHierarchicalXESInputStream(dbName, Query(pql), false).first()

    private fun computeMSE(traces: List<Trace>, predictor: Predictor): Map<String, Double> {
        val trainingSize = (.8 * traces.size).toInt()
        val training = traces.subList(0, trainingSize)
        val test = traces.subList(trainingSize, traces.size)
        predictor.fit(sequenceOf(Log(training.asSequence())))
        val sse = HashMap<String, Double>()
        val n = Counter<String>()
        for (trace in test) {
            predictor.startTrace()
            for (event in trace.events) {
                predictor.predict(event).forEach { (k, v) ->
                    val ground = (event.attributes.getOrNull(k) as Number?)?.toDouble()
                    if (ground !== null) {
                        sse.compute(k) { _, oldv -> (ground - v).pow(2) + (oldv ?: 0.0) }
                        n.inc(k)
                    }
                }
                predictor.observeEvent(event)
            }
        }
        return sse.mapValues { (k, v) -> v / n[k] }
    }

    @Test
    fun `regressive - JournalReviewExtra + VerticalARIMAPredictor`() {
        val traces = q(
            "select t:*, e:name, e:instance, sum(e:total), max(e:timestamp)-min(e:timestamp) where l:id=${logUUID} group by e:name, e:instance"
        ).traces.toList()
        with(computeMSE(traces, VerticalARIMAPredictor())) {
            assertEquals(2, size)
            assertDoubleEquals(0.008, this["sum(event:cost:total)"])
            assertDoubleEquals(1.754, this["max(event:time:timestamp) - min(event:time:timestamp)"])
        }
    }

    @Test
    fun `regressive - JournalReviewExtra + LinearRegressionPredictor`() {
        val traces = q(
            "select t:*, e:name, e:instance, sum(e:total), max(e:timestamp)-min(e:timestamp) where l:id=${logUUID} group by e:name, e:instance"
        ).traces.toList()
        with(computeMSE(traces, LinearRegressionPredictor())) {
            assertEquals(2, size)
            assertDoubleEquals(0.0, this["sum(event:cost:total)"])
            assertDoubleEquals(1.664, this["max(event:time:timestamp) - min(event:time:timestamp)"])
        }
    }

    @Test
    fun `regressive - JournalReviewExtra + FlatARIMAPredictor`() {
        val traces = q(
            "select t:*, e:name, e:instance, sum(e:total), max(e:timestamp)-min(e:timestamp) where l:id=${logUUID} group by e:name, e:instance"
        ).traces.toList()
        with(computeMSE(traces, FlatARIMAPredictor())) {
            assertEquals(2, size)
            assertDoubleEquals(0.274, this["sum(event:cost:total)"])
            assertDoubleEquals(3.1415, this["max(event:time:timestamp) - min(event:time:timestamp)"])
        }
    }
}