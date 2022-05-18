package processm.enhancement.simulation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import kotlin.math.abs

class SimulationTest {
    @Test
    fun `produces the same traces from sequential process model`() {
        val model = sequentialProcessModel()
        val instance = model.createInstance()
        val activitiesCount = model.activities.count()
        val simulation = Simulation(instance, nextActivityCumulativeDistributionFunction = emptyMap())

        val traces = simulation.generateTraces().take(10)

        traces.forEach { trace ->
            assertEquals(activitiesCount, trace.count())
        }
    }

    @Test
    fun `uses uniform distribution if no distribution is provided`() {
        val model = singleForkProcessModel()
        val instance = model.createInstance()
        val tracesCount = 1000
        val simulation = Simulation(instance, nextActivityCumulativeDistributionFunction = emptyMap())

        val traces = simulation.generateTraces().take(tracesCount)

        val (traceWithB, traceWithC) = traces.partition { trace -> trace[1].activity.name == "b" }
        assertEquals(tracesCount, traceWithB.count() + traceWithC.count())
        assertTrue(abs(traceWithB.count() - traceWithC.count()) < 100)
    }

    @Test
    fun `utilizes the distribution provided as CDF to generate traces`() {
        val model = singleForkProcessModel()
        val instance = model.createInstance()
        val activities = model.activities.map { it.name to it }.toMap()
        val tracesCount = 1000
        val simulation = Simulation(
            instance,
            mapOf(activities["a"]!! to
                    setOf(mapOf(
                       activities["b"]!! to 0.2,
                       activities["c"]!! to 1.0)
                    )))

        val traces = simulation.generateTraces().take(tracesCount)

        val (traceWithB, traceWithC) = traces.partition { trace -> trace[1].activity.name == "b" }
        assertEquals(tracesCount, traceWithB.count() + traceWithC.count())
        assertTrue(traceWithC.count() - traceWithB.count() > 500)
    }


    private fun sequentialProcessModel(): PetriNet {
        val start = Place()
        val c1 = Place()
        val c2 = Place()
        val c3 = Place()
        val c4 = Place()
        val c5 = Place()
        val end = Place()
        val a = Transition("a", listOf(start), listOf(c1))
        val b = Transition("b", listOf(c1), listOf(c2))
        val c = Transition("c", listOf(c2), listOf(c3))
        val d = Transition("d", listOf(c3), listOf(c4))
        val e = Transition("e", listOf(c4), listOf(c5))
        val f = Transition("h", listOf(c5), listOf(end))
        return PetriNet(
            listOf(start, c1, c2, c3, c4, c5, end),
            listOf(a, b, c, d, e, f),
            Marking(start),
            Marking(end)
        )
    }

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