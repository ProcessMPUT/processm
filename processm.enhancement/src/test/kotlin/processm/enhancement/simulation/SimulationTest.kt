package processm.enhancement.simulation

import org.apache.commons.math3.distribution.ExponentialDistribution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import processm.core.models.petrinet.*

class SimulationTest {

    @Test
    fun `PM book Fig 3 2 simulation runs`() {
        val start = Place()
        val c1 = Place()
        val c2 = Place()
        val c3 = Place()
        val c4 = Place()
        val c5 = Place()
        val end = Place()
        val a = Transition("a", listOf(start), listOf(c1, c2))
        val b = Transition("b", listOf(c1), listOf(c3))
        val c = Transition("c", listOf(c1), listOf(c3))
        val d = Transition("d", listOf(c2), listOf(c4))
        val e = Transition("e", listOf(c3, c4), listOf(c5))
        val f = Transition("f", listOf(c5), listOf(c1, c2))
        val g = Transition("g", listOf(c5), listOf(end))
        val h = Transition("h", listOf(c5), listOf(end))
        val net = PetriNet(
            listOf(start, c1, c2, c3, c4, c5, end),
            listOf(a, b, c, d, e, f, g, h),
            Marking(start),
            Marking(end)
        )

        val instance = net.createInstance()
        val simulation = Simulation(instance,
            nextActivityCumulativeDistributionFunction = emptyMap(), processInstanceStartTime = ExponentialDistribution(1.0))

        val sims = simulation.runSingleTrace().take(10).map { trace ->
            instance.setState(Marking(start))
            trace.joinToString("\n") { "${it.second}: ${it.first.name}" }
        }.toList()
        assertEquals(sims.count(), 10)
        val aa = 2
    }

    private fun `PM book Fig 3 2`(): PetriNetInstance {
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
        val net = PetriNet(
            listOf(start, c1, c2, c3, c4, c5, end),
            listOf(a, b, c, d, e, f),
            Marking(start),
            Marking(end)
        )

        return net.createInstance()
    }

    private fun sequentialProcessModelInstance(): PetriNetInstance {
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
        val net = PetriNet(
            listOf(start, c1, c2, c3, c4, c5, end),
            listOf(a, b, c, d, e, f),
            Marking(start),
            Marking(end)
        )

        return net.createInstance()
    }
}