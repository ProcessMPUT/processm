package processm.enhancement.simulation

import org.apache.commons.math3.distribution.ExponentialDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import org.junit.jupiter.api.Test
import processm.core.models.commons.ProcessModel
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import processm.enhancement.resources.BasicResource
import processm.enhancement.resources.ResourceBasedScheduler

class FullSimulationPipelineTest {
    @Test
    fun `PM book Fig 3 2 resources assignment`() {
        val net = `PM book Fig 3 2`()
        val instance = net.createInstance()
        val activities = net.activities.map { it.name to it }.toMap()

        val submitterRoles = setOf("submitter")
        val approverRoles = setOf("approver")

        val approver = BasicResource(approverRoles)
        val submitter = BasicResource(submitterRoles)
        val extraHelp = BasicResource(submitterRoles)

        val simulation = Simulation(instance,
            nextActivityCumulativeDistributionFunction = emptyMap())
        val scheduler = ResourceBasedScheduler(
            simulation,
            listOf(approver, submitter, extraHelp),
            mapOf(
                activities["a"]!! to submitterRoles,
                activities["b"]!! to submitterRoles,
                activities["c"]!! to submitterRoles,
                activities["d"]!! to submitterRoles,
                activities["f"]!! to submitterRoles,
                activities["g"]!! to submitterRoles,
                activities["h"]!! to submitterRoles,
                activities["e"]!! to approverRoles
            ),
            activities.map { it.value to NormalDistribution(5.0, 1.0) }.toMap(),
            ExponentialDistribution(3.0)
        )

        val tracesCount = 100
        val sims = scheduler.scheduleWith().take(tracesCount).toList()
        val logs = sims.map { trace ->
            trace.toList().joinToString("\n") { "${it.first.activity.name} after: ${it.first.executeAfter?.activity?.name}, start: ${it.second.first}, end: ${it.second.second}" }
        }.toList()
        val avgProcessing = sims.map { trace ->
            trace.maxOf { (_, duration) -> duration.second }.epochSecond - trace.minOf { (_, duration) -> duration.first }.epochSecond
        }.toTypedArray().average()
    }

    private fun `PM book Fig 3 2`(): ProcessModel {
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
        return PetriNet(
            listOf(start, c1, c2, c3, c4, c5, end),
            listOf(a, b, c, d, e, f, g, h),
            Marking(start),
            Marking(end)
        )
    }
}