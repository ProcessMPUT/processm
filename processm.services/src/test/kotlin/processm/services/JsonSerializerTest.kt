package processm.services

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.Step
import processm.core.log.Helpers
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.core.models.commons.Activity
import processm.core.models.commons.CausalArc
import processm.core.models.petrinet.Transition
import processm.core.models.processtree.ProcessTreeActivity
import processm.enhancement.kpi.Report
import processm.helpers.map2d.DoublingMap2D
import processm.helpers.stats.Distribution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
data class PseudoReport(val item: Map<String, @Contextual Distribution>)

class JsonSerializerTest {

    @Test
    fun `pseudoReport - encode`() {
        val item = mapOf("a" to Distribution(doubleArrayOf(1.0, 2.0)))
        assertEquals(
            JsonSerializer.parseToJsonElement("""{"item":{"a":{"min":1.0,"Q1":1.0,"median":1.5,"Q3":2.0,"max":2.0,"average":1.5,"standardDeviation":0.7071067811865476}}}"""),
            JsonSerializer.encodeToJsonElement(PseudoReport(item))
        )
    }

    @Test
    fun `pseudoReport - decode`() {
        val json =
            """{"item":{"a":{"min":1.0,"Q1":1.0,"median":1.5,"Q3":2.0,"max":2.0,"average":1.5,"standardDeviation":0.7071067811865476}}}"""
        JsonSerializer.decodeFromString<PseudoReport>(json)
    }

    @Test
    fun report() {
        val report = Report(
            logKPI = mapOf("a" to Distribution(doubleArrayOf(1.0, 2.0))),
            traceKPI = mapOf("t" to Distribution(doubleArrayOf(3.0, 5.0))),
            eventKPI = DoublingMap2D<String, Activity?, Distribution>().apply {
                set("e", Transition("start"), Distribution(doubleArrayOf(5.0, 6.0)))
                set("e", Transition("stop"), Distribution(doubleArrayOf(-5.0, -6.0)))
                set("e", null, Distribution(doubleArrayOf(0.11, 0.99)))
                set("f", ProcessTreeActivity("pt"), Distribution(doubleArrayOf(-.1, -101.0)))
            },
            arcKPI = DoublingMap2D<String, CausalArc, Distribution>().apply {
                set(
                    "e",
                    Dependency(Node("a"), Node("b")),
                    Distribution(doubleArrayOf(15.0, 16.0)),
                )
                set(
                    "e",
                    Dependency(Node("c"), Node("d")),
                    Distribution(doubleArrayOf(12.0, 17.0)),
                )
            },
            modelKPI = mapOf("a" to 1, "b" to 0xc0ffee),
            alignments = listOf(
                Alignment(
                    steps = listOf(
                        Step(
                            modelMove = Transition("a"),
                            modelState = null,
                            modelCause = emptyList(),
                            logMove = Helpers.event("a"),
                            logState = null,
                            type = DeviationType.None
                        ),
                        Step(
                            modelMove = null,
                            modelState = null,
                            modelCause = emptyList(),
                            logMove = Helpers.event("b"),
                            logState = null,
                            type = DeviationType.LogDeviation
                        )
                    ),
                    cost = 1
                )
            )
        )
        val s = JsonSerializer.encodeToJsonElement(report)
        // serialized by DoublingMap2DStringActivityDistributionSerializer
        with(s.jsonObject["eventKPI"]!!.jsonObject["e"]!!.jsonObject["\u0000"]!!.jsonObject) {
            assertTrue { containsKey("min") }
            assertTrue { containsKey("Q1") }
            assertTrue { containsKey("median") }
            assertTrue { containsKey("Q3") }
            assertTrue { containsKey("max") }
            assertTrue { containsKey("average") }
            assertTrue { containsKey("standardDeviation") }
        }
        // serialized by DistributionWebAPISerializer
        with(s.jsonObject["logKPI"]!!.jsonObject["a"]!!.jsonObject) {
            assertTrue { containsKey("min") }
            assertTrue { containsKey("Q1") }
            assertTrue { containsKey("median") }
            assertTrue { containsKey("Q3") }
            assertTrue { containsKey("max") }
            assertTrue { containsKey("average") }
            assertTrue { containsKey("standardDeviation") }
        }
    }
}