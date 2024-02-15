package processm.enhancement.kpi

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.Step
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.stats.Distribution
import processm.core.log.Helpers.event
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.core.models.commons.Activity
import processm.core.models.commons.CausalArc
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import processm.core.models.processtree.ProcessTreeActivity
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportTests {
    @Test
    fun serializationDeserialization() {
        val place = Place()
        val report = Report(
            logKPI = mapOf("a" to Distribution(doubleArrayOf(1.0, 2.0))),
            traceKPI = mapOf("t" to Distribution(doubleArrayOf(3.0, 5.0))),
            eventKPI = DoublingMap2D<String, Activity?, Distribution>().apply {
                set("e", Transition("start"), Distribution(doubleArrayOf(5.0, 6.0)))
                set("e", Transition("stop"), Distribution(doubleArrayOf(-5.0, -6.0)))
                set("e", null, Distribution(doubleArrayOf(0.11, 0.99)))
                set("f", ProcessTreeActivity("pt"), Distribution(doubleArrayOf(-.1, -101.0)))
            },
            inboundArcKPI = DoublingMap2D<String, CausalArc, Distribution>().apply {
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
            outboundArcKPI = DoublingMap2D<String, CausalArc, Distribution>().apply {
                set(
                    "e",
                    Dependency(Node("a"), Node("b")),
                    Distribution(doubleArrayOf(25.0, 26.0))
                )
                set(
                    "e",
                    Dependency(Node("c"), Node("d")),
                    Distribution(doubleArrayOf(22.0, 27.0))
                )
                set(
                    "e",
                    VirtualPetriNetCausalArc(
                        Transition("z", outPlaces = listOf(place)),
                        Transition("x", inPlaces = listOf(place)),
                        place
                    ),
                    Distribution(doubleArrayOf(32.0, 37.0))
                )
                set(
                    "e",
                    VirtualProcessTreeCausalArc(ProcessTreeActivity("z"), ProcessTreeActivity("x")),
                    Distribution(doubleArrayOf(42.0, 69.0))
                )
            },
            alignments = listOf(
                Alignment(
                    steps = listOf(
                        Step(
                            Transition("a"),
                            null,
                            event("a"),
                            null,
                            DeviationType.None
                        ),
                        Step(
                            null,
                            null,
                            event("b"),
                            null,
                            DeviationType.LogDeviation
                        )
                    ),
                    cost = 1
                )
            )
        )

        val json = report.toJson()
        val deserializedReport = Report.fromJson(json)

        assertEquals(report, deserializedReport)
    }

    @Test
    fun virtualPetriNetArcSerializationTest() {
        val place = Place()
        val a = Transition("a", outPlaces = listOf(place))
        val b = Transition("b", inPlaces = listOf(place))
        val arc = VirtualPetriNetCausalArc(a, b, place)
        val deserialized = Json.decodeFromString(serializer<VirtualPetriNetCausalArc>(), Json.encodeToString(arc))
        assertEquals(arc, deserialized)
    }

    @Test
    fun virtualProcessTreeArcSerializationTest() {
        val a = ProcessTreeActivity("a")
        val b = ProcessTreeActivity("b")
        val arc = VirtualProcessTreeCausalArc(a, b)
        val deserialized = Json.decodeFromString(serializer<VirtualProcessTreeCausalArc>(), Json.encodeToString(arc))
        assertEquals(arc, deserialized)
    }
}
