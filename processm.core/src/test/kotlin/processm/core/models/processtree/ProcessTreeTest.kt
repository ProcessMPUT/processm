package processm.core.models.processtree

import java.io.FileOutputStream
import javax.xml.stream.XMLOutputFactory
import kotlin.test.*

class ProcessTreeTest {
    @Test
    fun `Silent activity and activity without name are different`() {
        val firstModel = processTree { SilentActivity() }
        val secondModel = processTree { ProcessTreeActivity("") }

        assertFalse(firstModel.languageEqual(secondModel))
        assertFalse(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Model without activities language equal to another model without activities`() {
        val firstModel = processTree { null }
        val secondModel = processTree { null }

        assertTrue(firstModel.languageEqual(secondModel))
        assertTrue(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Models with different activities (single activity in tree) can not be language equal`() {
        val firstModel = processTree { ProcessTreeActivity("A") }
        val secondModel = processTree { ProcessTreeActivity("B") }

        assertFalse(firstModel.languageEqual(secondModel))
        assertFalse(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Models with XOR can store children in any order`() {
        val firstModel = processTree {
            Exclusive(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C")
            )
        }
        val secondModel = processTree {
            Exclusive(
                ProcessTreeActivity("C"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("A")
            )
        }

        assertTrue(firstModel.languageEqual(secondModel))
        assertTrue(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Models must store the same attributes, not only the same count`() {
        val firstModel = processTree {
            Exclusive(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B")
            )
        }
        val secondModel = processTree {
            Exclusive(
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D")
            )
        }

        assertFalse(firstModel.languageEqual(secondModel))
        assertFalse(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Models with SEQUENCE must store children in the same order`() {
        val firstModel = processTree {
            Sequence(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C")
            )
        }
        val secondModel = processTree {
            Sequence(
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("A")
            )
        }
        val extraModel = processTree {
            Sequence(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C")
            )
        }

        assertFalse(firstModel.languageEqual(secondModel))
        assertFalse(secondModel.languageEqual(firstModel))

        assertTrue(firstModel.languageEqual(extraModel))
        assertTrue(extraModel.languageEqual(firstModel))
    }

    @Test
    fun `Redo loop must contain the same first child`() {
        val firstModel = processTree {
            RedoLoop(
                SilentActivity(),
                Exclusive(
                    ProcessTreeActivity("A"),
                    ProcessTreeActivity("B")
                )
            )
        }
        val secondModel = processTree {
            RedoLoop(
                ProcessTreeActivity(""),
                Exclusive(
                    ProcessTreeActivity("B"),
                    ProcessTreeActivity("A")
                )
            )
        }

        assertFalse(firstModel.languageEqual(secondModel))
        assertFalse(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Redo loop can contain children in any order but first must match`() {
        val firstModel = processTree {
            RedoLoop(
                SilentActivity(),
                Exclusive(
                    ProcessTreeActivity("A"),
                    ProcessTreeActivity("B")
                ),
                Sequence(
                    ProcessTreeActivity("C"),
                    ProcessTreeActivity("D")
                )
            )
        }
        val secondModel = processTree {
            RedoLoop(
                SilentActivity(),
                Sequence(
                    ProcessTreeActivity("C"),
                    ProcessTreeActivity("D")
                ),
                Exclusive(
                    ProcessTreeActivity("B"),
                    ProcessTreeActivity("A")
                )
            )
        }

        assertTrue(firstModel.languageEqual(secondModel))
        assertTrue(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Parallel can contain children in any order`() {
        val firstModel = processTree {
            Parallel(
                Exclusive(
                    ProcessTreeActivity("A"),
                    ProcessTreeActivity("B")
                ),
                Sequence(
                    ProcessTreeActivity("C"),
                    ProcessTreeActivity("D")
                ),
                ProcessTreeActivity("X")
            )
        }
        val secondModel = processTree {
            Parallel(
                ProcessTreeActivity("X"),
                Sequence(
                    ProcessTreeActivity("C"),
                    ProcessTreeActivity("D")
                ),
                Exclusive(
                    ProcessTreeActivity("B"),
                    ProcessTreeActivity("A")
                )
            )
        }

        assertTrue(firstModel.languageEqual(secondModel))
        assertTrue(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Node without reference to own parent if root`() {
        val a = ProcessTreeActivity("A")
        with(processTree { Sequence(a) }) {
            assertNull(root!!.parent)
            assertEquals(toString(), "→(A)")
        }
    }

    @Test
    fun `Node with reference to own parent`() {
        val a = ProcessTreeActivity("A")
        val b = ProcessTreeActivity("B")
        val model = processTree { Sequence(a, b) }

        with(model.root!!) {
            assertNull(parent)

            children.forEach { childrenNode ->
                assertEquals(childrenNode.parent, this)
            }
        }

        assertEquals(model.toString(), "→(A,B)")
    }

    @Test
    fun `Model without activities`() {
        val model = processTree { null }
        assertNull(model.root)
        assertEquals(model.toString(), "")
    }

    @Test
    fun `Model with silent activity`() {
        val model = processTree {
            RedoLoop(
                SilentActivity(),
                ProcessTreeActivity("A")
            )
        }

        with(model.root as RedoLoop) {
            assertEquals(children.size, 2)

            with(children[0] as SilentActivity) {
                assertTrue(children.isEmpty())
            }

            with(children[1] as ProcessTreeActivity) {
                assertEquals(name, "A")
                assertTrue(children.isEmpty())
            }
        }

        assertEquals(model.toString(), "⟲(τ,A)")
    }

    @Test
    fun `Model with activity as root - only activity in model`() {
        val model = processTree {
            ProcessTreeActivity("A")
        }

        with(model.root as ProcessTreeActivity) {
            assertEquals(name, "A")
            assertTrue(children.isEmpty())
        }

        assertEquals(model.toString(), "A")
    }

    @Test
    fun `Process Mining - Figure 7 point 20`() {
        // Log file: [{a,b,c,d}, {a,c,b,d}, {a,e,d}]
        val model = processTree {
            Sequence(
                ProcessTreeActivity("A"),
                Exclusive(
                    Parallel(
                        ProcessTreeActivity("B"),
                        ProcessTreeActivity("C")
                    ),
                    ProcessTreeActivity("E")
                ),
                ProcessTreeActivity("D")
            )
        }

        assertEquals(model.toString(), "→(A,×(∧(B,C),E),D)")

        with(model.root!!) {
            assert(this is Sequence)
            assertEquals(children.size, 3)

            with(children[0] as ProcessTreeActivity) {
                assertEquals(name, "A")
                assertTrue(children.isEmpty())
            }

            with(children[1]) {
                assert(this is Exclusive)
                assertEquals(children.size, 2)

                with(children[0]) {
                    assert(this is Parallel)
                    assertEquals(children.size, 2)

                    with(children[0] as ProcessTreeActivity) {
                        assertEquals(name, "B")
                        assertTrue(children.isEmpty())
                    }

                    with(children[1] as ProcessTreeActivity) {
                        assertEquals(name, "C")
                        assertTrue(children.isEmpty())
                    }
                }

                with(children[1] as ProcessTreeActivity) {
                    assertEquals(name, "E")
                    assertTrue(children.isEmpty())
                }
            }

            with(children[2] as ProcessTreeActivity) {
                assertEquals(name, "D")
                assertTrue(children.isEmpty())
            }
        }
    }

    @Test
    fun `Process Mining - Figure 7 point 24`() {
        // Log file: [{a,b,c,d}, {a,c,b,d}, {a,b,c,e,f,b,c,d}, {a,c,b,e,f,b,c,d}, {a,b,c,e,f,c,b,d}, {a,c,b,e,f,b,c,e,f,c,b,d}]
        val model = processTree {
            Sequence(
                ProcessTreeActivity("A"),
                RedoLoop(
                    Parallel(
                        ProcessTreeActivity("B"),
                        ProcessTreeActivity("C")
                    ),
                    Sequence(
                        ProcessTreeActivity("E"),
                        ProcessTreeActivity("F")
                    )
                ),
                ProcessTreeActivity("D")
            )
        }

        assertEquals(model.toString(), "→(A,⟲(∧(B,C),→(E,F)),D)")

        with(model.root!!) {
            assert(this is Sequence)
            assertEquals(children.size, 3)

            with(children[0] as ProcessTreeActivity) {
                assertEquals(name, "A")
                assertTrue(children.isEmpty())
            }

            with(children[1]) {
                assert(this is RedoLoop)
                assertEquals(children.size, 2)

                with(children[0]) {
                    assert(this is Parallel)
                    assertEquals(children.size, 2)

                    with(children[0] as ProcessTreeActivity) {
                        assertEquals(name, "B")
                        assertTrue(children.isEmpty())
                    }

                    with(children[1] as ProcessTreeActivity) {
                        assertEquals(name, "C")
                        assertTrue(children.isEmpty())
                    }
                }

                with(children[1]) {
                    assert(this is Sequence)
                    assertEquals(children.size, 2)

                    with(children[0] as ProcessTreeActivity) {
                        assertEquals(name, "E")
                        assertTrue(children.isEmpty())
                    }

                    with(children[1] as ProcessTreeActivity) {
                        assertEquals(name, "F")
                        assertTrue(children.isEmpty())
                    }
                }
            }

            with(children[2] as ProcessTreeActivity) {
                assertEquals(name, "D")
                assertTrue(children.isEmpty())
            }
        }
    }

    @Test
    fun `Process Mining - Figure 7 point 27`() {
        // Log file: [{a,b,d,e,h}, {a,d,c,e,g}, {a,c,d,e,f,b,d,e,g}, {a,d,b,e,h}, {a,c,d,e,f,d,c,e,f,c,d,e,h}, {a,c,d,e,g}]
        val model = processTree {
            Sequence(
                ProcessTreeActivity("A"),
                RedoLoop(
                    Sequence(
                        Parallel(
                            Exclusive(
                                ProcessTreeActivity("B"),
                                ProcessTreeActivity("C")
                            ),
                            ProcessTreeActivity("D")
                        ),
                        ProcessTreeActivity("E")
                    ),
                    ProcessTreeActivity("F")
                ),
                Exclusive(
                    ProcessTreeActivity("G"),
                    ProcessTreeActivity("H")
                )
            )
        }

        FileOutputStream("tree.txt").use { received ->
            model.toPTML(XMLOutputFactory.newInstance().createXMLStreamWriter(received))
        }

        assertEquals(model.toString(), "→(A,⟲(→(∧(×(B,C),D),E),F),×(G,H))")

        with(model.root!!) {
            assert(this is Sequence)
            assertEquals(children.size, 3)

            with(children[0] as ProcessTreeActivity) {
                assertEquals(name, "A")
                assertTrue(children.isEmpty())
            }

            with(children[1]) {
                assert(this is RedoLoop)
                assertEquals(children.size, 2)

                with(children[0]) {
                    assert(this is Sequence)
                    assertEquals(children.size, 2)

                    with(children[0]) {
                        assert(this is Parallel)
                        assertEquals(children.size, 2)

                        with(children[0]) {
                            assert(this is Exclusive)
                            assertEquals(children.size, 2)

                            with(children[0] as ProcessTreeActivity) {
                                assertEquals(name, "B")
                                assertTrue(children.isEmpty())
                            }

                            with(children[1] as ProcessTreeActivity) {
                                assertEquals(name, "C")
                                assertTrue(children.isEmpty())
                            }
                        }

                        with(children[1] as ProcessTreeActivity) {
                            assertEquals(name, "D")
                            assertTrue(children.isEmpty())
                        }
                    }

                    with(children[1] as ProcessTreeActivity) {
                        assertEquals(name, "E")
                        assertTrue(children.isEmpty())
                    }
                }

                with(children[1] as ProcessTreeActivity) {
                    assertEquals(name, "F")
                    assertTrue(children.isEmpty())
                }
            }

            with(children[2]) {
                assert(this is Exclusive)
                assertEquals(children.size, 2)

                with(children[0] as ProcessTreeActivity) {
                    assertEquals(name, "G")
                    assertTrue(children.isEmpty())
                }

                with(children[1] as ProcessTreeActivity) {
                    assertEquals(name, "H")
                    assertTrue(children.isEmpty())
                }
            }
        }
    }

    @Test
    fun `start nodes example from PM page 82`() {
        assertEquals(setOf(SilentActivity()), processTree { SilentActivity() }.startActivities.toSet())
        assertEquals(setOf(ProcessTreeActivity("a")), processTree { ProcessTreeActivity("a") }.startActivities.toSet())
        assertEquals(
            setOf(ProcessTreeActivity("a")),
            processTree { Sequence(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")) }.startActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")),
            processTree { Exclusive(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")) }.startActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")),
            processTree { Parallel(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")) }.startActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a")),
            processTree { RedoLoop(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")) }.startActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a")),
            processTree {
                Sequence(
                    ProcessTreeActivity("a"),
                    Exclusive(ProcessTreeActivity("b"), ProcessTreeActivity("c")),
                    Parallel(ProcessTreeActivity("a"), ProcessTreeActivity("a"))
                )
            }.startActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a"), ProcessTreeActivity("c"), SilentActivity()),
            processTree {
                Exclusive(
                    SilentActivity(),
                    ProcessTreeActivity("a"),
                    SilentActivity(),
                    Sequence(
                        SilentActivity(),
                        ProcessTreeActivity("b")
                    ),
                    Parallel(
                        ProcessTreeActivity("c"),
                        SilentActivity()
                    )
                )
            }.startActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a")),
            processTree {
                RedoLoop(
                    ProcessTreeActivity("a"),
                    SilentActivity(),
                    ProcessTreeActivity("c")
                )
            }.startActivities.toSet()
        )
    }

    @Test
    fun `end nodes example from PM page 82`() {
        assertEquals(setOf(SilentActivity()), processTree { SilentActivity() }.endActivities.toSet())
        assertEquals(setOf(ProcessTreeActivity("a")), processTree { ProcessTreeActivity("a") }.endActivities.toSet())
        assertEquals(
            setOf(ProcessTreeActivity("c")),
            processTree { Sequence(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")) }.endActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")),
            processTree { Exclusive(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")) }.endActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")),
            processTree { Parallel(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")) }.endActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a")),
            processTree { RedoLoop(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c")) }.endActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a")),
            processTree {
                Sequence(
                    ProcessTreeActivity("a"),
                    Exclusive(ProcessTreeActivity("b"), ProcessTreeActivity("c")),
                    Parallel(ProcessTreeActivity("a"), ProcessTreeActivity("a"))
                )
            }.endActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a"), ProcessTreeActivity("b"), ProcessTreeActivity("c"), SilentActivity()),
            processTree {
                Exclusive(
                    SilentActivity(),
                    ProcessTreeActivity("a"),
                    SilentActivity(),
                    Sequence(
                        SilentActivity(),
                        ProcessTreeActivity("b")
                    ),
                    Parallel(
                        ProcessTreeActivity("c"),
                        SilentActivity()
                    )
                )
            }.endActivities.toSet()
        )
        assertEquals(
            setOf(ProcessTreeActivity("a")),
            processTree {
                RedoLoop(
                    ProcessTreeActivity("a"),
                    SilentActivity(),
                    ProcessTreeActivity("c")
                )
            }.endActivities.toSet()
        )
    }

    private fun kotlin.sequences.Sequence<InternalNode>.expecting(vararg outcomes: List<Node>) =
        assertEquals(
            outcomes.toList(),
            this.filter { it.isRealDecision }.map { it.possibleOutcomes.map { it.node } }.toList()
        )


    @Test
    fun `decision points ⟲(⟲(a1,a2,a3),⟲(b1,b2,b3),⟲(c1,c2,c3))`() {
        val a1 = ProcessTreeActivity("a1")
        val a2 = ProcessTreeActivity("a2")
        val a3 = ProcessTreeActivity("a3")
        val b1 = ProcessTreeActivity("b1")
        val b2 = ProcessTreeActivity("b2")
        val b3 = ProcessTreeActivity("b3")
        val c1 = ProcessTreeActivity("c1")
        val c2 = ProcessTreeActivity("c2")
        val c3 = ProcessTreeActivity("c3")
        val a = RedoLoop(a1, a2, a3)
        val b = RedoLoop(b1, b2, b3)
        val c = RedoLoop(c1, c2, c3)
        val top = RedoLoop(a, b, c)
        processTree { top }.decisionPoints.expecting(
            listOf(top.endLoopActivity, b, c),
            listOf(a.endLoopActivity, a2, a3),
            listOf(b.endLoopActivity, b2, b3),
            listOf(c.endLoopActivity, c2, c3)
        )
    }

    @Test
    fun `decision points ∧(a,×(b,c),⟲(d,e,f))`() {
        val a = ProcessTreeActivity("a")
        val b = ProcessTreeActivity("b")
        val c = ProcessTreeActivity("c")
        val d = ProcessTreeActivity("d")
        val e = ProcessTreeActivity("e")
        val f = ProcessTreeActivity("f")
        val loop = RedoLoop(d, e, f)
        val tree = processTree {
            Parallel(
                a,
                Exclusive(b, c),
                loop
            )
        }
        tree.decisionPoints.expecting(listOf(b, c), listOf(loop.endLoopActivity, e, f))
    }
}