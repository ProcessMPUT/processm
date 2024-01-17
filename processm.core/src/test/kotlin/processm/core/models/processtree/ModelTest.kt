package processm.core.models.processtree

import kotlin.test.*

class ModelTest {
    private val A = ProcessTreeActivity("A")
    private val B = ProcessTreeActivity("B")
    private val C = ProcessTreeActivity("C")
    private val D = ProcessTreeActivity("D")
    private val E = ProcessTreeActivity("E")
    private val F = ProcessTreeActivity("F")
    private val G = ProcessTreeActivity("G")
    private val H = ProcessTreeActivity("H")

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
        val firstModel = processTree { A }
        val secondModel = processTree { B }

        assertFalse(firstModel.languageEqual(secondModel))
        assertFalse(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Models with XOR can store children in any order`() {
        val firstModel = processTree { Exclusive(A, B, C) }
        val secondModel = processTree { Exclusive(C, B, A) }

        assertTrue(firstModel.languageEqual(secondModel))
        assertTrue(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Models must store the same attributes, not only the same count`() {
        val firstModel = processTree { Exclusive(A, B) }
        val secondModel = processTree { Exclusive(C, D) }

        assertFalse(firstModel.languageEqual(secondModel))
        assertFalse(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Models with SEQUENCE must store children in the same order`() {
        val firstModel = processTree { Sequence(A, B, C) }
        val secondModel = processTree { Sequence(B, C, A) }
        val extraModel = processTree { Sequence(A, B, C) }

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
                Exclusive(A, B)
            )
        }
        val secondModel = processTree {
            RedoLoop(
                ProcessTreeActivity(""),
                Exclusive(B, A)
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
                Exclusive(A, B),
                Sequence(C, D)
            )
        }
        val secondModel = processTree {
            RedoLoop(
                SilentActivity(),
                Sequence(C, D),
                Exclusive(B, A)
            )
        }

        assertTrue(firstModel.languageEqual(secondModel))
        assertTrue(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Parallel can contain children in any order`() {
        val firstModel = processTree {
            Parallel(
                Exclusive(A, B),
                Sequence(C, D),
                E
            )
        }
        val secondModel = processTree {
            Parallel(
                E,
                Sequence(C, D),
                Exclusive(B, A)
            )
        }

        assertTrue(firstModel.languageEqual(secondModel))
        assertTrue(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Node without reference to own parent if root`() {
        with(processTree { Sequence(A) }) {
            assertNull(root!!.parent)
            assertEquals(toString(), "→(A)")
        }
    }

    @Test
    fun `Node with reference to own parent`() {
        val model = processTree { Sequence(A, B) }

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
        val model = processTree { RedoLoop(SilentActivity(), A) }

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
        val model = processTree { A }

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
                A,
                Exclusive(
                    Parallel(B, C),
                    E
                ),
                D
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
                A,
                RedoLoop(
                    Parallel(B, C),
                    Sequence(E, F)
                ),
                D
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
                A,
                RedoLoop(
                    Sequence(
                        Parallel(
                            Exclusive(B, C),
                            D
                        ),
                        E
                    ),
                    F
                ),
                Exclusive(G, H)
            )
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
}