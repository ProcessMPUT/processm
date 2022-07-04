package processm.enhancement.simulation

import processm.conformance.PetriNets
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.toFlatSequence
import processm.core.log.takeTraces
import processm.enhancement.simulation.Logs.table81
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The tests in this class verify whether the [MarkovSimulation] returns traces from the same distribution as the complying
 * models.
 */
@OptIn(InMemoryXESProcessing::class)
class SimulationDistributionTest {
    companion object {
        private const val TOLERANCE = 0.1
    }

    @Test
    fun `Table 8 1 conforming model`() {
        val numTraces = 10000
        val prob = table81.toFlatSequence().getDirectlyFollowsProbabilities()
        val simulation = MarkovSimulation(
            processModel = PetriNets.fig32,
            activityTransitionsProbabilityWeights = prob
        )
        val stream = simulation.takeTraces(numTraces)
        val simulatedProb = stream.getDirectlyFollowsProbabilities()

        for (prev in prob.rows) {
            val rowData = prob.getRow(prev)
            val simRowData = simulatedProb.getRow(prev)
            for ((next, p) in rowData) {
                assertEquals(p, simRowData[next]!!, TOLERANCE)
            }
        }
    }
}
