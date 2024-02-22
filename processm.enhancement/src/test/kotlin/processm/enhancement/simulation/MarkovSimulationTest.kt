package processm.enhancement.simulation

import processm.conformance.PetriNets
import processm.conformance.models.alignments.CompositeAligner
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.takeTraces
import processm.core.models.causalnet.CausalNets
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.*

@OptIn(InMemoryXESProcessing::class)
class MarkovSimulationTest {

    lateinit var random: Random

    @BeforeTest
    fun beforeTest() {
        random = Random(42)
    }

    @Test
    fun `produces the same traces from sequential process model`() {
        val model = sequentialProcessModel()
        val activitiesCount = model.activities.count()
        val simulation = MarkovSimulation(model, random = random)

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
        val simulation = MarkovSimulation(model, random = random)

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
        val simulation = MarkovSimulation(model, transitionsProbabilitiesWeights, random = random)

        val stream = simulation.takeTraces(tracesCount)
        val log = HoneyBadgerHierarchicalXESInputStream(stream).first()

        val (traceWithB, traceWithC) = log.traces.partition { trace -> trace.events.elementAt(1).conceptName == "b" }
        assertEquals(tracesCount, traceWithB.count() + traceWithC.count())
        assertTrue(traceWithC.count() - traceWithB.count() > 500)
    }

    @Test
    fun `model that terminates in the non-terminal state throw an exception`() {
        val simulation = MarkovSimulation(CausalNets.fig316, random = random)
        val exception = assertFailsWith<IllegalStateException> {
            simulation.takeTraces(10).forEach { /*just iterate*/ }
        }
        assertTrue("terminal non-final state" in exception.message!!)
    }

    @Test
    fun `generated log is perfectly aligned with the model`() {
        val simulation = MarkovSimulation(CausalNets.fig312, random = random)
        val tracesCount = 1000
        val log = HoneyBadgerHierarchicalXESInputStream(simulation.takeTraces(tracesCount)).first()
        val aligner = CompositeAligner(CausalNets.fig312)
        val alignments = aligner.align(log)

        for (alignment in alignments)
            assertEquals(0, alignment.cost)
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
