package processm.enhancement.kpi

import processm.core.helpers.Distribution
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.models.commons.Activity
import processm.core.models.petrinet.Transition
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportTests {
    @Test
    fun serializationDeserialization() {
        val report = Report(
            logKPI = mapOf("a" to Distribution(doubleArrayOf(1.0, 2.0))),
            traceKPI = mapOf("t" to Distribution(doubleArrayOf(3.0, 5.0))),
            eventKPI = DoublingMap2D<String, Activity?, Distribution>().apply {
                set("e", Transition("start"), Distribution(doubleArrayOf(5.0, 6.0)))
                set("e", Transition("stop"), Distribution(doubleArrayOf(-5.0, -6.0)))
                set("e", null, Distribution(doubleArrayOf(0.11, 0.99)))
            }
        )

        val json = report.toJson()
        val deserializedReport = Report.fromJson(json)

        assertEquals(report, deserializedReport)
    }
}
