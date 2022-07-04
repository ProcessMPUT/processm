package processm.enhancement.simulation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import processm.conformance.PetriNets
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.takeTraces
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import kotlin.math.abs

@OptIn(InMemoryXESProcessing::class)
class MarkovSimulationTest {
    @Test
    fun `produces the same traces from sequential process model`() {
        val model = sequentialProcessModel()
        val activitiesCount = model.activities.count()
        val simulation = MarkovSimulation(model)

        val stream = simulation.takeTraces(10)
        val log = HoneyBadgerHierarchicalXESInputStream(stream).first()

        log.traces.forEach { trace ->
            assertEquals(activitiesCount, trace.events.count())
        }
    }

    @Test
    fun `uses uniform distribution if no distribution is provided`() {
        val model = singleForkProcessModel()
        val tracesCount = 1000
        val simulation = MarkovSimulation(model)

        val stream = simulation.takeTraces(tracesCount)
        val log = HoneyBadgerHierarchicalXESInputStream(stream).first()

        val (traceWithB, traceWithC) = log.traces.partition { trace -> trace.events.elementAt(1).conceptName == "b" }
        assertEquals(tracesCount, traceWithB.count() + traceWithC.count())
        assertTrue(abs(traceWithB.count() - traceWithC.count()) < 100)
    }

    @Test
    fun `utilizes the distribution provided as CDF to generate traces`() {
        val model = singleForkProcessModel()
        val tracesCount = 1000
        val transitionsProbabilitiesWeights = DoublingMap2D<String, String, Double>()
        transitionsProbabilitiesWeights["a", "b"] = 1.0
        transitionsProbabilitiesWeights["a", "c"] = 5.0
        val simulation = MarkovSimulation(model, transitionsProbabilitiesWeights)

        val stream = simulation.takeTraces(tracesCount)
        val log = HoneyBadgerHierarchicalXESInputStream(stream).first()

        val (traceWithB, traceWithC) = log.traces.partition { trace -> trace.events.elementAt(1).conceptName == "b" }
        assertEquals(tracesCount, traceWithB.count() + traceWithC.count())
        assertTrue(traceWithC.count() - traceWithB.count() > 500)
    }


    private fun sequentialProcessModel() = PetriNets.getSequence("a", "b", "c", "d", "e", "h")

    private fun singleForkProcessModel(): PetriNet {
        val start = Place()
        val c1 = Place()
        val c2 = Place()
        val end = Place()
        val a = Transition("a", listOf(start), listOf(c1))
        val b = Transition("b", listOf(c1), listOf(c2))
        val c = Transition("c", listOf(c1), listOf(c2))
        val d = Transition("d", listOf(c2), listOf(end))
        return PetriNet(
            listOf(start, c1, c2, end),
            listOf(a, b, c, d),
            Marking(start),
            Marking(end)
        )
    }
}
