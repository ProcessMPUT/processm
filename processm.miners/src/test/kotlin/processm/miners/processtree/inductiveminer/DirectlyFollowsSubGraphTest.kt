package processm.miners.processtree.inductiveminer

import org.junit.jupiter.api.assertThrows
import processm.core.models.processtree.ProcessTreeActivity
import processm.miners.processtree.directlyfollowsgraph.Arc
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
    private val registerRequest = ProcessTreeActivity("register request")
    private val checkTicket = ProcessTreeActivity("check ticket")
    private val examineThoroughly = ProcessTreeActivity("examine thoroughly")
    private val examineCasually = ProcessTreeActivity("examine casually")
    private val decide = ProcessTreeActivity("decide")
    private val rejectRequest = ProcessTreeActivity("reject request")
    private val reinitiateRequest = ProcessTreeActivity("reinitiate request")
    private val payCompensation = ProcessTreeActivity("pay compensation")

    private fun activitiesSet(l: Collection<ProcessTreeActivity>) = HashSet<ProcessTreeActivity>().also {
        it.addAll(l)
    }

    @Test
    fun `Possible to finish calculations if contains only one activity`() {
        val activities = activitiesSet(listOf(A))
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertTrue(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `NOT possible to finish calculations - more than one activity on graph`() {
        val activities = activitiesSet(listOf(A, B))
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertFalse(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `NOT possible to finish calculations - connection between activity`() {
        val activities = activitiesSet(listOf(A))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arc ->
                arc[A] = Arc()
                conn[A] = arc
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)

        assertFalse(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `Fetch alone activity from graph`() {
        val activities = activitiesSet(listOf(A))
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertEquals(A, graph.finishCalculations())
    }

    @Test
    fun `Exception if can't fetch activity`() {
        val activities = activitiesSet(listOf(A, B))
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertThrows<IllegalStateException> {
            graph.finishCalculations()
        }.also { exception ->
            assertEquals("SubGraph is not split yet. Can't fetch activity!", exception.message)
        }
    }

    @Test
    fun `Graph without separated parts`() {
        // Based on Figure 7.21 PM book: L1 = {[a,b,c,d], [a,c,b,d], [a,e,d]}, part G1
        val activities = activitiesSet(listOf(A, B, C, D, E))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[C] = Arc()
                arcs[E] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[D] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[D] = Arc()
                conn[C] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                conn[E] = arcs
            }
        }

        val noAssignment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        assertNull(noAssignment)
    }

    @Test
    fun `Graph without separated parts but with not ordered activity`() {
        val activities = activitiesSet(listOf(A, B, C, D, E, F))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[C] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[F] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[F] = Arc()
                conn[C] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[E] = Arc()
                conn[D] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[F] = Arc()
                conn[E] = arcs
            }
        }

        val noAssignment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        assertNull(noAssignment)
    }

    @Test
    fun `Graph with separated parts`() {
        // Based on Figure 7.21 PM book: L1 = {[a,b,c,d], [a,c,b,d], [a,e,d]} part G1b
        val activities = activitiesSet(listOf(B, C, E))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                conn[C] = arcs
            }
        }

        val assignment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        assertNotNull(assignment)

        // B & C in group
        assertEquals(assignment[B], assignment[C])

        // E with different label
        assertNotEquals(assignment[B], assignment[E])
    }

    @Test
    fun `Split graph based on list of activities - separated groups`() {
        val activities = activitiesSet(listOf(A, B, C))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[A] = Arc()
                conn[B] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)

        assertEquals(2, graph.children.size)
    }

    @Test
    fun `Redo loop operator as default rule of subGraph cut`() {
        val activities = activitiesSet(listOf(A, B, C))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[C] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[A] = Arc()
                arcs[C] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[A] = Arc()
                conn[C] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val result = graph.finishWithDefaultRule()

        assertEquals("⟲(τ,A,B,C)", result.toString())
    }

    @Test
    fun `Calculate strongly connected components based on example from ProcessMining book`() {
        // Based on Figure 7.21 PM book: L1 = {[a,b,c,d], [a,c,b,d], [a,e,d]}, part G1
        val activities = activitiesSet(listOf(A, B, C, D, E))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[C] = Arc()
                arcs[E] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[D] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[D] = Arc()
                conn[C] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                conn[E] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val result = graph.stronglyConnectedComponents()

        assertEquals(4, result.size)

        assertTrue(result[0].containsAll(listOf(D)))
        assertTrue(result[1].containsAll(listOf(B, C)))
        assertTrue(result[2].containsAll(listOf(E)))
        assertTrue(result[3].containsAll(listOf(A)))
    }

    @Test
    fun `Strongly connected components`() {
        // Based on https://en.wikipedia.org/wiki/Strongly_connected_component
        val activities = activitiesSet(listOf(A, B, C, D, E, F, G, H))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[E] = Arc()
                arcs[F] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                arcs[G] = Arc()
                conn[C] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[H] = Arc()
                conn[D] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[A] = Arc()
                arcs[F] = Arc()
                conn[E] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[G] = Arc()
                conn[F] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[F] = Arc()
                conn[G] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                arcs[G] = Arc()
                conn[H] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val result = graph.stronglyConnectedComponents()

        assertEquals(3, result.size)

        assertTrue(result.contains(setOf(F, G)))
        assertTrue(result.contains(setOf(C, D, H)))
        assertTrue(result.contains(setOf(A, B, E)))
    }

    @Test
    fun `Strongly connected components into connection matrix`() {
        // Based on https://en.wikipedia.org/wiki/Strongly_connected_component
        val activities = activitiesSet(listOf(A, B, C, D, E, F, G, H))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[E] = Arc()
                arcs[F] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                arcs[G] = Arc()
                conn[C] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[H] = Arc()
                conn[D] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[A] = Arc()
                arcs[F] = Arc()
                conn[E] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[G] = Arc()
                conn[F] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[F] = Arc()
                conn[G] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                arcs[G] = Arc()
                conn[H] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
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
        // Based on Figure 7.21 PM book: L1 = {[a,b,c,d], [a,c,b,d], [a,e,d]}, part G1
        val activities = activitiesSet(listOf(A, B, C, D, E))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[C] = Arc()
                arcs[E] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[D] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[D] = Arc()
                conn[C] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                conn[E] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
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
        // L1 = [a,b,c,a]
        val activities = activitiesSet(listOf(A, B, C))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[A] = Arc()
                conn[C] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val stronglyConnected = graph.stronglyConnectedComponents()
        val noAssignment = graph.calculateSequentialCut(stronglyConnected)

        assertNull(noAssignment)
    }

    @Test
    fun `Calculate sequential cut based on Wikipedia example`() {
        // Based on https://en.wikipedia.org/wiki/Strongly_connected_component
        val activities = activitiesSet(listOf(A, B, C, D, E, F, G, H))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[E] = Arc()
                arcs[F] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                arcs[G] = Arc()
                conn[C] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[H] = Arc()
                conn[D] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[A] = Arc()
                arcs[F] = Arc()
                conn[E] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[G] = Arc()
                conn[F] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[F] = Arc()
                conn[G] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                arcs[G] = Arc()
                conn[H] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
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
    fun `Prepare negated connections - eliminate loops from graph`() {
        val activities = activitiesSet(
            listOf(
                registerRequest,
                checkTicket,
                examineThoroughly,
                examineCasually,
                decide,
                reinitiateRequest,
                payCompensation,
                rejectRequest
            )
        )
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[decide] = Arc()
                arcs[examineCasually] = Arc()
                arcs[examineThoroughly] = Arc()
                conn[checkTicket] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[decide] = Arc()
                arcs[checkTicket] = Arc()
                conn[examineThoroughly] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[decide] = Arc()
                arcs[checkTicket] = Arc()
                conn[examineCasually] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[checkTicket] = Arc()
                arcs[examineCasually] = Arc()
                arcs[examineThoroughly] = Arc()
                conn[registerRequest] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[reinitiateRequest] = Arc()
                arcs[payCompensation] = Arc()
                arcs[rejectRequest] = Arc()
                conn[decide] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[checkTicket] = Arc()
                arcs[examineCasually] = Arc()
                arcs[examineThoroughly] = Arc()
                conn[reinitiateRequest] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val response = graph.negateDFGConnections()

        assertEquals(16, connections.values.sumBy { it.size })
        assertEquals(12, response.values.sumBy { it.size })

        assertFalse(response[checkTicket]!!.containsKey(examineCasually))
        assertFalse(response[checkTicket]!!.containsKey(examineThoroughly))
        assertFalse(response[examineCasually]!!.containsKey(checkTicket))
        assertFalse(response[examineThoroughly]!!.containsKey(checkTicket))

        assertTrue(response[checkTicket]!!.containsKey(decide))
        assertTrue(response[examineThoroughly]!!.containsKey(decide))
        assertTrue(response[examineCasually]!!.containsKey(decide))
        assertTrue(response[registerRequest]!!.containsKey(checkTicket))
        assertTrue(response[registerRequest]!!.containsKey(examineCasually))
        assertTrue(response[registerRequest]!!.containsKey(examineThoroughly))
        assertTrue(response[decide]!!.containsKey(reinitiateRequest))
        assertTrue(response[decide]!!.containsKey(rejectRequest))
        assertTrue(response[decide]!!.containsKey(payCompensation))
        assertTrue(response[reinitiateRequest]!!.containsKey(examineCasually))
        assertTrue(response[reinitiateRequest]!!.containsKey(examineThoroughly))
        assertTrue(response[reinitiateRequest]!!.containsKey(checkTicket))
    }

    @Test
    fun `Graph without loops between activities - no changes in connections mapping`() {
        val activities = activitiesSet(listOf(A, B, C, D, E, F))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[C] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[F] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[F] = Arc()
                conn[C] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[E] = Arc()
                conn[D] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[F] = Arc()
                conn[E] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val response = graph.negateDFGConnections()

        assertEquals(7, connections.values.sumBy { it.size })
        assertEquals(7, response.values.sumBy { it.size })
        assertEquals(connections, response)
    }

    @Test
    fun `Start activities in current sub graph`() {
        val activities = activitiesSet(listOf(B, C, D))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[D] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[D] = Arc()
                conn[C] = arcs
            }
        }
        val initialConnections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[C] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[D] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[D] = Arc()
                conn[C] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections, initialConnections)
        val response = graph.currentStartActivities()

        assertEquals(0, graph.currentEndActivities().size)
        assertEquals(2, response.size)

        assertTrue(response.contains(B))
        assertTrue(response.contains(C))
    }

    @Test
    fun `End activities in current sub graph`() {
        val activities = activitiesSet(listOf(B, C, D))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                conn[C] = arcs
            }
        }
        val initialConnections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                conn[C] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[E] = Arc()
                conn[D] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections, initialConnections)
        val response = graph.currentEndActivities()

        assertEquals(1, response.size)
        assertTrue(response.contains(D))
    }

    @Test
    fun `Contain start and end activities in assignment`() {
        val activities = activitiesSet(listOf(checkTicket, examineThoroughly, examineCasually))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[examineCasually] = Arc()
                arcs[examineThoroughly] = Arc()
                conn[checkTicket] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[checkTicket] = Arc()
                conn[examineThoroughly] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[checkTicket] = Arc()
                conn[examineCasually] = arcs
            }
        }
        val initialConnections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[decide] = Arc()
                arcs[examineCasually] = Arc()
                arcs[examineThoroughly] = Arc()
                conn[checkTicket] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[decide] = Arc()
                arcs[checkTicket] = Arc()
                conn[examineThoroughly] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[decide] = Arc()
                arcs[checkTicket] = Arc()
                conn[examineCasually] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[checkTicket] = Arc()
                arcs[examineCasually] = Arc()
                arcs[examineThoroughly] = Arc()
                conn[registerRequest] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[reinitiateRequest] = Arc()
                arcs[payCompensation] = Arc()
                arcs[rejectRequest] = Arc()
                conn[decide] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[checkTicket] = Arc()
                arcs[examineCasually] = Arc()
                arcs[examineThoroughly] = Arc()
                conn[reinitiateRequest] = arcs
            }
        }
        val assignment = HashMap<ProcessTreeActivity, Int>().also {
            it[checkTicket] = 1
            it[examineCasually] = 1
            it[examineThoroughly] = 1
        }

        val graph = DirectlyFollowsSubGraph(activities, connections, initialConnections)
        assertTrue(graph.isStartAndEndActivityInEachGroup(assignment))
    }

    @Test
    fun `Calculate parallel cut based on Process Mining 7-21 book - activities B and C with different labels`() {
        // Based on Figure 7.21 PM book: L1 = {[a,b,c,d], [a,c,b,d], [a,e,d]}, part G1d
        val activities = activitiesSet(listOf(B, C))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                conn[C] = arcs
            }
        }
        val initialConnections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[C] = Arc()
                arcs[E] = Arc()
                conn[A] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[C] = Arc()
                arcs[D] = Arc()
                conn[B] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[B] = Arc()
                arcs[D] = Arc()
                conn[C] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[D] = Arc()
                conn[E] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections, initialConnections)
        val assignment = graph.calculateParallelCut()

        assertNotNull(assignment)

        assertEquals(2, assignment.size)
        assertNotEquals(assignment[B], assignment[C])
    }
}