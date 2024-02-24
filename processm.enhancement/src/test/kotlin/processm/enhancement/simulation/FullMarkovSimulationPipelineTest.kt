package processm.enhancement.simulation

import org.apache.commons.math3.distribution.ExponentialDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import processm.conformance.PetriNets
import processm.core.log.InferTimes
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.takeTraces
import processm.core.models.causalnet.CausalNets
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.BasicMetadata.LEAD_TIME
import processm.core.models.processtree.ProcessTrees
import processm.enhancement.resources.ApplyResourceBasedScheduling
import processm.enhancement.resources.BasicResource
import java.time.Duration
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test

class FullMarkovSimulationPipelineTest {

    lateinit var random: Random

    @BeforeTest
    fun beforeTest() {
        random = Random(42)
    }

    @Test
    fun `Petri Net from PM book Fig 3 2`() {
        runSimulationPipeline(PetriNets.fig32)
    }

    @Test
    fun `Causal Net from PM book Fig 3 12`() {
        runSimulationPipeline(CausalNets.fig312)
    }

    @Test
    fun `Process Tree from PM book Fig 7 27`() {
        runSimulationPipeline(ProcessTrees.fig727)
    }

    @OptIn(InMemoryXESProcessing::class)
    private fun runSimulationPipeline(processModel: ProcessModel) {
        val activities = processModel.activities.map { it.name to it }.toMap()

        val submitterRoles = setOf("submitter")
        val approverRoles = setOf("approver")

        val approver = BasicResource("Kate", approverRoles)
        val submitter = BasicResource("Mary", submitterRoles)
        val extraHelp = BasicResource("Tom", submitterRoles)

        val simulation = MarkovSimulation(processModel, random = random)
        val stream = InferTimes(
            ApplyResourceBasedScheduling(
                simulation,
                listOf(approver, submitter, extraHelp),
                mapOf(
                    "a" to submitterRoles,
                    "b" to submitterRoles,
                    "c" to submitterRoles,
                    "d" to submitterRoles,
                    "f" to submitterRoles,
                    "g" to submitterRoles,
                    "h" to submitterRoles,
                    "e" to approverRoles
                ),
                activities.map { (activityName, _) -> activityName to NormalDistribution(5.0, 1.0) }.toMap(),
                ExponentialDistribution(3.0)
            )
        )

        val tracesCount = 5
        val log = HoneyBadgerHierarchicalXESInputStream(stream.takeTraces(tracesCount)).first()
        val traces = log.traces.map { trace ->
            trace.events
                .joinToString("\n") { "${it.identityId} ${it.conceptName} after: ${it.attributes.getOrNull("cause")}, timestamp: ${it.timeTimestamp}" }
        }.toList()
        println(traces)
        val avgLeadTime = log.traces.map { trace ->
            trace[LEAD_TIME.urn]?.let { Duration.parse(it as CharSequence).toSeconds() } ?: 0L
        }.average()
        println("Avg lead time: $avgLeadTime ms")
    }
}
