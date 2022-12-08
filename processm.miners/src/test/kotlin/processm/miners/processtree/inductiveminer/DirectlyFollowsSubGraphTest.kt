package processm.miners.processtree.inductiveminer

import processm.core.log.Helpers.logFromString
import processm.core.models.processtree.ProcessTreeActivity
import processm.miners.processtree.directlyfollowsgraph.DirectlyFollowsGraph
import kotlin.test.*

internal class DirectlyFollowsSubGraphTest {
    private val A = ProcessTreeActivity("A")
    private val B = ProcessTreeActivity("B")
    private val C = ProcessTreeActivity("C")
    private val D = ProcessTreeActivity("D")
    private val E = ProcessTreeActivity("E")
    private val F = ProcessTreeActivity("F")
    private val G = ProcessTreeActivity("G")
    private val H = ProcessTreeActivity("H")

    private fun activitiesSet(l: Collection<ProcessTreeActivity>) = HashSet<ProcessTreeActivity>().also {
        it.addAll(l)
    }

    @Test
    fun `Possible to finish calculations if contains only one activity`() {
        val log = logFromString(
            """
            A
            """.trimIndent()
        )

        val activities = activitiesSet(listOf(A))
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val graph = DirectlyFollowsSubGraph(activities, dfg)

        assertTrue(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `NOT possible to finish calculations - more than one activity on graph`() {
        val log = logFromString(
            """
            A B
            """.trimIndent()
        )

        val activities = activitiesSet(listOf(A, B))
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val graph = DirectlyFollowsSubGraph(activities, dfg)

        assertFalse(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `Can finish calculations - only one activity, found redo loop`() {
        val log = logFromString(
            """
            A A A
            """.trimIndent()
        )
        val activities = activitiesSet(listOf(A))
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val graph = DirectlyFollowsSubGraph(activities, dfg)

        assertTrue(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `Fetch alone activity from graph`() {
        val log = logFromString(
            """
            A
            """.trimIndent()
        )
        val activities = activitiesSet(listOf(A))
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val graph = DirectlyFollowsSubGraph(activities, dfg)

        assertEquals(A, graph.finishCalculations())
    }

    @Test
    fun `Exception if can't fetch activity`() {
        val log = logFromString(
            """
            A B
            """.trimIndent()
        )
        val activities = activitiesSet(listOf(A, B))
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val graph = DirectlyFollowsSubGraph(activities, dfg)

        assertFailsWith<IllegalStateException> {
            graph.finishCalculations()
        }.also { exception ->
            assertEquals("SubGraph is not split yet. Can't fetch activity!", exception.message)
        }
    }

    @Test
    fun `Graph without separated parts (PM Book, 7-21 part G1)`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            A E D
            """.trimIndent()
        )
        val activities = activitiesSet(listOf(A, B, C, D, E))
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val noAssignment = DirectlyFollowsSubGraph(activities, dfg).calculateExclusiveCut()

        assertNull(noAssignment)
    }

    @Test
    fun `Graph without separated parts but with not ordered activity`() {
        val log = logFromString(
            """
            A B F
            A C F
            D E F
            """.trimIndent()
        )
        val activities = activitiesSet(listOf(A, B, C, D, E, F))
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val noAssignment = DirectlyFollowsSubGraph(activities, dfg).calculateExclusiveCut()

        assertNull(noAssignment)
    }

    @Test
    fun `Graph with separated parts (PM Book, 7-21 part G1b)`() {
        val log = logFromString(
            """
            B C
            C B
            """.trimIndent()
        )
        val activities = activitiesSet(listOf(B, C, E))
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val assignment = DirectlyFollowsSubGraph(activities, dfg).calculateExclusiveCut()

        assertNotNull(assignment)

        // B & C in group
        assertEquals(assignment[B], assignment[C])

        // E with different label
        assertNotEquals(assignment[B], assignment[E])
    }

    @Test
    fun `Split graph based on list of activities - separated groups`() {
        val log = logFromString(
            """
            A B A
            """.trimIndent()
        )
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val activities = activitiesSet(listOf(A, B, C))
        val graph = DirectlyFollowsSubGraph(activities, dfg)

        assertEquals(2, graph.children.size)
    }

    @Test
    fun `Redo loop operator as default rule of subGraph cut`() {
        val log = logFromString(
            """
            A B A
            A C A
            A B C A
            """.trimIndent()
        )
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val activities = activitiesSet(listOf(A, B, C))
        val result = DirectlyFollowsSubGraph(activities, dfg).finishWithDefaultRule()

        assertEquals("⟲(τ,A,B,C)", result.toString())
    }

    @Test
    fun `Calculate strongly connected components based on PM Book, 7-21 part G1`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            A E D
            """.trimIndent()
        )
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val activities = activitiesSet(listOf(A, B, C, D, E))
        val result = DirectlyFollowsSubGraph(activities, dfg).stronglyConnectedComponents()

        assertEquals(4, result.size)

        assertTrue(result[0].containsAll(listOf(D)))
        assertTrue(result[1].containsAll(listOf(B, C)))
        assertTrue(result[2].containsAll(listOf(E)))
        assertTrue(result[3].containsAll(listOf(A)))
    }

    @Test
    fun `Strongly connected components`() {
        // Based on https://en.wikipedia.org/wiki/Strongly_connected_component
        val log = logFromString(
            """
            A B C D C
            A B C D H D
            A B C D H G F
            A B C G F
            A B E A
            A B E F G F
            A B F G F
            """.trimIndent()
        )
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val activities = activitiesSet(listOf(A, B, C, D, E, F, G, H))
        val result = DirectlyFollowsSubGraph(activities, dfg).stronglyConnectedComponents()

        assertEquals(3, result.size)

        assertTrue(result.contains(setOf(F, G)))
        assertTrue(result.contains(setOf(C, D, H)))
        assertTrue(result.contains(setOf(A, B, E)))
    }

    @Test
    fun `Strongly connected components into connection matrix`() {
        // Based on https://en.wikipedia.org/wiki/Strongly_connected_component
        val log = logFromString(
            """
            A B C D C
            A B C D H D
            A B C D H G F
            A B C G F
            A B E A
            A B E F G F
            A B F G F
            """.trimIndent()
        )
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val activities = activitiesSet(listOf(A, B, C, D, E, F, G, H))
        val graph = DirectlyFollowsSubGraph(activities, dfg)
        val stronglyConnected = graph.stronglyConnectedComponents()
        val result = graph.connectionMatrix(stronglyConnected)

        assertEquals(3, result.size)
        assertEquals(0, result[0][0])
        assertEquals(1, result[0][1])
        assertEquals(1, result[0][2])
        assertEquals(-1, result[1][0])
        assertEquals(0, result[1][1])
        assertEquals(1, result[1][2])
        assertEquals(-1, result[2][0])
        assertEquals(-1, result[2][1])
        assertEquals(0, result[2][2])
    }

    @Test
    fun `Calculate sequential cut based on Process Mining 7-21 book`() {
        val log = logFromString(
            """
                A B C D
                A C B D
                A E D
            """.trimIndent()
        )
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val activities = activitiesSet(listOf(A, B, C, D, E))
        val graph = DirectlyFollowsSubGraph(activities, dfg)
        val stronglyConnected = graph.stronglyConnectedComponents()
        val assignment = graph.calculateSequentialCut(stronglyConnected)

        assertNotNull(assignment)

        assertEquals(1, assignment[A])

        assertEquals(2, assignment[B])
        assertEquals(2, assignment[C])
        assertEquals(2, assignment[E])

        assertEquals(3, assignment[D])
    }

    @Test
    fun `Calculate sequential cut not found - loop here`() {
        val log = logFromString(
            """
                A B C A
            """.trimIndent()
        )
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val activities = activitiesSet(listOf(A, B, C))
        val graph = DirectlyFollowsSubGraph(activities, dfg)
        val stronglyConnected = graph.stronglyConnectedComponents()
        val noAssignment = graph.calculateSequentialCut(stronglyConnected)

        assertNull(noAssignment)
    }

    @Test
    fun `Calculate sequential cut based on Wikipedia example`() {
        val log = logFromString(
            """
            A B C D C
            A B C D H D
            A B C D H G F
            A B C G F
            A B E A
            A B E F G F
            A B F G F
            """.trimIndent()
        )
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val activities = activitiesSet(listOf(A, B, C, D, E, F, G, H))
        val graph = DirectlyFollowsSubGraph(activities, dfg)
        val stronglyConnected = graph.stronglyConnectedComponents()
        val assignment = graph.calculateSequentialCut(stronglyConnected)

        assertNotNull(assignment)

        assertEquals(1, assignment[A])
        assertEquals(1, assignment[B])
        assertEquals(1, assignment[E])

        assertEquals(2, assignment[C])
        assertEquals(2, assignment[D])
        assertEquals(2, assignment[H])

        assertEquals(3, assignment[F])
        assertEquals(3, assignment[G])
    }

    @Test
    fun `Start activities in current sub graph`() {
        val log = logFromString(
            """
                A B C D
                A C B D
            """.trimIndent()
        )
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val activities = activitiesSet(listOf(B, C, D))
        val graph = DirectlyFollowsSubGraph(activities, dfg)
        val response = graph.currentStartActivities()

        assertEquals(1, graph.currentEndActivities().size)
        assertEquals(2, response.size)

        assertTrue(response.contains(B))
        assertTrue(response.contains(C))
    }

    @Test
    fun `End activities in current sub graph`() {
        val log = logFromString(
            """
                A B C D E
            """.trimIndent()
        )
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val activities = activitiesSet(listOf(B, C, D))
        val graph = DirectlyFollowsSubGraph(activities, dfg)
        val response = graph.currentEndActivities()

        assertEquals(1, response.size)
        assertTrue(response.contains(D))
    }

    @Test
    fun `Calculate parallel cut based on Process Mining 7-21 book - activities B and C with different labels`() {
        val log = logFromString(
            """
                A B C D
                A C B D
                A E D
            """.trimIndent()
        )
        val dfg = DirectlyFollowsGraph().also { it.discover(sequenceOf(log)) }
        val activities = activitiesSet(listOf(B, C))
        val graph = DirectlyFollowsSubGraph(activities, dfg)
        val assignment = graph.calculateParallelCut()

        assertNotNull(assignment)

        assertEquals(2, assignment.size)
        assertNotEquals(assignment[B], assignment[C])
    }
}
