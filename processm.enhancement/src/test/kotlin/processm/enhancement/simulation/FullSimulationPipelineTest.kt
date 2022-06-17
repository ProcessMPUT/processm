package processm.enhancement.simulation

import org.apache.commons.math3.distribution.ExponentialDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import org.junit.jupiter.api.Test
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.commons.ProcessModel
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import processm.core.models.processtree.ProcessTree
import processm.enhancement.resources.BasicResource
import processm.enhancement.resources.ResourceBasedScheduler

class FullSimulationPipelineTest {

    @Test
    fun `Petri Net from PM book Fig 3 2`() {
        val model = `PM book Fig 3 2`()
        runSimulationPipeline(model)
    }

    @Test
    fun `Causal Net from PM book Fig 3 12`() {
        val model = `PM book Fig 3 12`()
        runSimulationPipeline(model)
    }

    @Test
    fun `Process Tree from PM book Fig 7 27`() {
        val model = `PM book Fig 7 27`()
        runSimulationPipeline(model)
    }

    private fun runSimulationPipeline(processModel: ProcessModel) {
        val activities = processModel.activities.map { it.name to it }.toMap()

        val submitterRoles = setOf("submitter")
        val approverRoles = setOf("approver")

        val approver = BasicResource(approverRoles)
        val submitter = BasicResource(submitterRoles)
        val extraHelp = BasicResource(submitterRoles)

        val simulation = Simulation(processModel)
        val scheduler = ResourceBasedScheduler(
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

        val tracesCount = 5
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

    private fun `PM book Fig 3 12`(): ProcessModel {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val f = Node("f")
        val g = Node("g")
        val h = Node("h")
        val z = Node("z")
        return causalnet {
            start = a
            end = z
            a splits b + d
            a splits c + d

            a joins b
            f joins b
            b splits e

            a joins c
            f joins c
            c splits e

            a joins d
            f joins d
            d splits e

            b + d join e
            c + d join e
            e splits g
            e splits h
            e splits f

            e joins f
            f splits b + d
            f splits c + d

            e joins g
            g splits z

            e joins h
            h splits z

            g joins z
            h joins z
        }
    }

    private fun `PM book Fig 7 27`(): ProcessModel {
        return ProcessTree.parse("→(a,⟲(→(∧(×(b,c),d),e),f),×(g,h))")
    }
}