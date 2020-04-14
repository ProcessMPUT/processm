package processm.miners.processtree.inductiveminer

import org.junit.jupiter.api.assertThrows
import processm.core.models.processtree.ProcessTreeActivity
import processm.miners.processtree.directlyfollowsgraph.Arc
import kotlin.test.*

internal class DirectlyFollowsSubGraphTest {
    private fun activitiesSet(l: Collection<ProcessTreeActivity>) = HashSet<ProcessTreeActivity>().also {
        it.addAll(l)
    }

    @Test
    fun `Possible to finish calculations if contains only one activity`() {
        val activities = activitiesSet(listOf(ProcessTreeActivity("A")))
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertTrue(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `NOT possible to finish calculations - more than one activity on graph`() {
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B")
            )
        )
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertFalse(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `NOT possible to finish calculations - connection between activity`() {
        val activities = activitiesSet(listOf(ProcessTreeActivity("A")))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arc ->
                arc[ProcessTreeActivity("A")] = Arc().increment()
                conn[ProcessTreeActivity("A")] = arc
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)

        assertFalse(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `Fetch alone activity from graph`() {
        val activities = activitiesSet(listOf(ProcessTreeActivity("A")))
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertEquals(ProcessTreeActivity("A"), graph.finishCalculations())
    }

    @Test
    fun `Exception if can't fetch activity`() {
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B")
            )
        )
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
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("E")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
        }

        val noAssignment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        assertNull(noAssignment)
    }

    @Test
    fun `Graph without separated parts but with not ordered activity`() {
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E"),
                ProcessTreeActivity("F")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("C")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("E")] = Arc()
                conn[ProcessTreeActivity("D")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
        }

        val noAssignment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        assertNull(noAssignment)
    }

    @Test
    fun `Graph with separated parts`() {
        // Based on Figure 7.21 PM book: L1 = {[a,b,c,d], [a,c,b,d], [a,e,d]} part G1b
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("E")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
        }

        val assignment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        assertNotNull(assignment)

        // B & C in group
        assertEquals(assignment[ProcessTreeActivity("B")], assignment[ProcessTreeActivity("C")])

        // E with different label
        assertNotEquals(assignment[ProcessTreeActivity("B")], assignment[ProcessTreeActivity("E")])
    }

    @Test
    fun `Split graph based on list of activities - separated groups`() {
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val result = graph.splitIntoSubGraphs(graph.calculateExclusiveCut()!!)

        assertEquals(2, result.size)
    }

    @Test
    fun `Redo loop operator as default rule of subGraph cut`() {
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("C")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                arcs[ProcessTreeActivity("C")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val result = graph.finishWithDefaultRule()

        assertEquals("⟲(τ,A,B,C)", result.toString())
    }

    @Test
    fun `Calculate strongly connected components based on example from ProcessMining book`() {
        // Based on Figure 7.21 PM book: L1 = {[a,b,c,d], [a,c,b,d], [a,e,d]}, part G1
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("E")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val result = graph.stronglyConnectedComponents()

        assertEquals(4, result.size)
        assertTrue(result[0].containsAll(listOf(ProcessTreeActivity("D"))))
        assertTrue(result[1].containsAll(listOf(ProcessTreeActivity("B"), ProcessTreeActivity("C"))))
        assertTrue(result[2].containsAll(listOf(ProcessTreeActivity("E"))))
        assertTrue(result[3].containsAll(listOf(ProcessTreeActivity("A"))))
    }

    @Test
    fun `Strongly connected components`() {
        // Based on https://en.wikipedia.org/wiki/Strongly_connected_component
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E"),
                ProcessTreeActivity("F"),
                ProcessTreeActivity("G"),
                ProcessTreeActivity("H")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("E")] = Arc()
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("H")] = Arc()
                conn[ProcessTreeActivity("D")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("F")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("G")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("H")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val result = graph.stronglyConnectedComponents()

        assertEquals(3, result.size)
        assertTrue(result.contains(setOf(ProcessTreeActivity("F"), ProcessTreeActivity("G"))))
        assertTrue(result.contains(setOf(ProcessTreeActivity("C"), ProcessTreeActivity("D"), ProcessTreeActivity("H"))))
        assertTrue(result.contains(setOf(ProcessTreeActivity("A"), ProcessTreeActivity("B"), ProcessTreeActivity("E"))))
    }

    @Test
    fun `Strongly connected components into connection matrix`() {
        // Based on https://en.wikipedia.org/wiki/Strongly_connected_component
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E"),
                ProcessTreeActivity("F"),
                ProcessTreeActivity("G"),
                ProcessTreeActivity("H")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("E")] = Arc()
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("H")] = Arc()
                conn[ProcessTreeActivity("D")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("F")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("G")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("H")] = arcs
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
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("E")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val stronglyConnected = graph.stronglyConnectedComponents()
        val assignment = graph.calculateSequentialCut(stronglyConnected)

        assertNotNull(assignment)

        assertEquals(1, assignment[ProcessTreeActivity("A")])

        assertEquals(2, assignment[ProcessTreeActivity("B")])
        assertEquals(2, assignment[ProcessTreeActivity("C")])
        assertEquals(2, assignment[ProcessTreeActivity("E")])

        assertEquals(3, assignment[ProcessTreeActivity("D")])
    }

    @Test
    fun `Calculate sequential cut not found - loop here`() {
        // L1 = [a,b,c,a]
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
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
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E"),
                ProcessTreeActivity("F"),
                ProcessTreeActivity("G"),
                ProcessTreeActivity("H")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("E")] = Arc()
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("H")] = Arc()
                conn[ProcessTreeActivity("D")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("F")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("G")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("H")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val stronglyConnected = graph.stronglyConnectedComponents()
        val assignment = graph.calculateSequentialCut(stronglyConnected)

        assertNotNull(assignment)

        assertEquals(1, assignment[ProcessTreeActivity("A")])
        assertEquals(1, assignment[ProcessTreeActivity("B")])
        assertEquals(1, assignment[ProcessTreeActivity("E")])

        assertEquals(2, assignment[ProcessTreeActivity("C")])
        assertEquals(2, assignment[ProcessTreeActivity("D")])
        assertEquals(2, assignment[ProcessTreeActivity("H")])

        assertEquals(3, assignment[ProcessTreeActivity("F")])
        assertEquals(3, assignment[ProcessTreeActivity("G")])
    }

    @Test
    fun `Prepare negated connections - eliminate loops from graph`() {
        val registerRequest = ProcessTreeActivity("register request")
        val checkTicket = ProcessTreeActivity("check ticket")
        val examineThoroughly = ProcessTreeActivity("examine thoroughly")
        val examineCasually = ProcessTreeActivity("examine casually")
        val decide = ProcessTreeActivity("decide")
        val rejectRequest = ProcessTreeActivity("reject request")
        val reinitiateRequest = ProcessTreeActivity("reinitiate request")
        val payCompensation = ProcessTreeActivity("pay compensation")

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
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E"),
                ProcessTreeActivity("F")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("C")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("E")] = Arc()
                conn[ProcessTreeActivity("D")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val response = graph.negateDFGConnections()

        assertEquals(7, connections.values.sumBy { it.size })
        assertEquals(7, response.values.sumBy { it.size })
        assertEquals(connections, response)
    }
}