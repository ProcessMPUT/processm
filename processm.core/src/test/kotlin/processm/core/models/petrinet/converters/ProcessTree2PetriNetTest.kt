package processm.core.models.petrinet.converters

import processm.core.models.petrinet.PetriNetInstance
import processm.core.models.processtree.ProcessTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProcessTree2PetriNetTest {

    @Test
    fun sequence() {
        with(ProcessTree.parse("→(A,B,C)").toPetriNet()) {
            assertEquals(3, transitions.size)
            val a = transitions.single { it.name == "A" }
            val b = transitions.single { it.name == "B" }
            val c = transitions.single { it.name == "C" }
            assertEquals(initialMarking.keys, a.inPlaces)
            assertEquals(b.inPlaces, a.outPlaces)
            assertEquals(b.outPlaces, c.inPlaces)
            assertEquals(finalMarking.keys, c.outPlaces)
        }
    }

    @Test
    fun exclusive() {
        with(ProcessTree.parse("×(A,B,C)").toPetriNet()) {
            assertEquals(3, available(initialMarking).count())
            assertEquals(2, places.size)
        }
    }

    @Test
    fun parallel() {
        with(ProcessTree.parse("∧(A,B,C)").toPetriNet()) {
            assertEquals(5, transitions.size)
            val a = transitions.single { it.name == "A" }
            val b = transitions.single { it.name == "B" }
            val c = transitions.single { it.name == "C" }
            assertNotEquals(a.inPlaces, b.inPlaces)
            assertNotEquals(a.inPlaces, c.inPlaces)
            assertNotEquals(b.inPlaces, c.inPlaces)
            assertNotEquals(a.outPlaces, b.outPlaces)
            assertNotEquals(a.outPlaces, c.outPlaces)
            assertNotEquals(b.outPlaces, c.outPlaces)
            assertEquals(1, a.inPlaces.size)
            assertEquals(1, b.inPlaces.size)
            assertEquals(1, c.inPlaces.size)
            assertEquals(1, a.outPlaces.size)
            assertEquals(1, b.outPlaces.size)
            assertEquals(1, c.outPlaces.size)
        }
    }

    @Test
    fun redo() {
        with(ProcessTree.parse("⟲(A,B,C)").toPetriNet()) {
            with(PetriNetInstance(this)) {
                availableActivityExecutions.single().execute()
                assertEquals("A", availableActivityExecutions.single().activity.name)
                availableActivityExecutions.single().execute()
                with(availableActivityExecutions.toList()) {
                    assertEquals(3, size)
                    assertEquals(1, count { it.activity.isSilent })
                    assertEquals(1, count { it.activity.name == "B" && !it.activity.isSilent })
                    assertEquals(1, count { it.activity.name == "C" && !it.activity.isSilent })
                    single { it.activity.name == "B" }.execute()
                }
                assertEquals("A", availableActivityExecutions.single().activity.name)
                availableActivityExecutions.single().execute()
                with(availableActivityExecutions.toList()) {
                    assertEquals(3, size)
                    assertEquals(1, count { it.activity.isSilent })
                    assertEquals(1, count { it.activity.name == "B" && !it.activity.isSilent })
                    assertEquals(1, count { it.activity.name == "C" && !it.activity.isSilent })
                    single { it.activity.name == "C" }.execute()
                }
                assertEquals("A", availableActivityExecutions.single().activity.name)
                availableActivityExecutions.single().execute()
                with(availableActivityExecutions.toList()) {
                    assertEquals(3, size)
                    assertEquals(1, count { it.activity.isSilent })
                    assertEquals(1, count { it.activity.name == "B" && !it.activity.isSilent })
                    assertEquals(1, count { it.activity.name == "C" && !it.activity.isSilent })
                    single { it.activity.isSilent }.execute()
                }
                assertTrue { isFinalState }
            }
        }
    }

    @Test
    fun selfLoop() {
        with(ProcessTree.parse("⟲(A,τ)").toPetriNet()) {
            assertEquals(2, transitions.size)
            val a = transitions.single { it.name == "A" && !it.isSilent }
            val tau = transitions.single { it.isSilent }
            assertEquals(3, places.size)
            assertEquals(1, initialMarking.size)
            assertEquals(initialMarking.keys + a.outPlaces, a.inPlaces)
            assertEquals(a.outPlaces, tau.inPlaces)
            assertEquals(tau.outPlaces, finalMarking.keys)
        }
    }
}
