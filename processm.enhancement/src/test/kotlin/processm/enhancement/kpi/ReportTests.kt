package processm.enhancement.kpi

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.stats.Distribution
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.core.models.commons.Activity
import processm.core.models.commons.Arc
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
            arcKPI = DoublingMap2D<String, Arc, ArcKPI>().apply {
                set(
                    "e",
                    Dependency(Node("a"), Node("b")),
                    ArcKPI(
                        Distribution(doubleArrayOf(15.0, 16.0)),
                        Distribution(doubleArrayOf(25.0, 26.0))
                    )
                )
                set(
                    "e",
                    Dependency(Node("c"), Node("d")),
                    ArcKPI(
                        Distribution(doubleArrayOf(12.0, 17.0)),
                        Distribution(doubleArrayOf(22.0, 27.0))
                    )
                )
                set(
                    "e",
                    VirtualPetriNetArc(
                        Transition("z", outPlaces = listOf(place)),
                        Transition("x", inPlaces = listOf(place)),
                        place
                    ),
                    ArcKPI(
                        null,
                        Distribution(doubleArrayOf(32.0, 37.0))
                    )
                )
                set(
                    "e",
                    VirtualProcessTreeArc(ProcessTreeActivity("z"), ProcessTreeActivity("x")),
                    ArcKPI(
                        null,
                        Distribution(doubleArrayOf(42.0, 69.0))
                    )
                )
            }
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
        val arc = VirtualPetriNetArc(a, b, place)
        val deserialized = Json.decodeFromString(serializer<VirtualPetriNetArc>(), Json.encodeToString(arc))
        assertEquals(arc, deserialized)
    }

    @Test
    fun virtualProcessTreeArcSerializationTest() {
        val a = ProcessTreeActivity("a")
        val b = ProcessTreeActivity("b")
        val arc = VirtualProcessTreeArc(a, b)
        val deserialized = Json.decodeFromString(serializer<VirtualProcessTreeArc>(), Json.encodeToString(arc))
        assertEquals(arc, deserialized)
    }
}
