package processm.core.models.processtree

import kotlin.test.*

class ModelTest {
    @Test
    fun `Silent activity and activity without name are different`() {
        val firstModel = processTree { SilentActivity() }
        val secondModel = processTree { Activity("") }

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
        val firstModel = processTree { Activity("A") }
        val secondModel = processTree { Activity("B") }

        assertFalse(firstModel.languageEqual(secondModel))
        assertFalse(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Models with XOR can store children in any order`() {
        val firstModel = processTree {
            Exclusive(
                Activity("A"),
                Activity("B"),
                Activity("C")
            )
        }
        val secondModel = processTree {
            Exclusive(
                Activity("C"),
                Activity("B"),
                Activity("A")
            )
        }

        assertTrue(firstModel.languageEqual(secondModel))
        assertTrue(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Models must store the same attributes, not only the same count`() {
        val firstModel = processTree {
            Exclusive(
                Activity("A"),
                Activity("B")
            )
        }
        val secondModel = processTree {
            Exclusive(
                Activity("C"),
                Activity("D")
            )
        }

        assertFalse(firstModel.languageEqual(secondModel))
        assertFalse(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Models with SEQUENCE must store children in the same order`() {
        val firstModel = processTree {
            Sequence(
                Activity("A"),
                Activity("B"),
                Activity("C")
            )
        }
        val secondModel = processTree {
            Sequence(
                Activity("B"),
                Activity("C"),
                Activity("A")
            )
        }
        val extraModel = processTree {
            Sequence(
                Activity("A"),
                Activity("B"),
                Activity("C")
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
                    Activity("A"),
                    Activity("B")
                )
            )
        }
        val secondModel = processTree {
            RedoLoop(
                Activity(""),
                Exclusive(
                    Activity("B"),
                    Activity("A")
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
                    Activity("A"),
                    Activity("B")
                ),
                Sequence(
                    Activity("C"),
                    Activity("D")
                )
            )
        }
        val secondModel = processTree {
            RedoLoop(
                SilentActivity(),
                Sequence(
                    Activity("C"),
                    Activity("D")
                ),
                Exclusive(
                    Activity("B"),
                    Activity("A")
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
                    Activity("A"),
                    Activity("B")
                ),
                Sequence(
                    Activity("C"),
                    Activity("D")
                ),
                Activity("X")
            )
        }
        val secondModel = processTree {
            Parallel(
                Activity("X"),
                Sequence(
                    Activity("C"),
                    Activity("D")
                ),
                Exclusive(
                    Activity("B"),
                    Activity("A")
                )
            )
        }

        assertTrue(firstModel.languageEqual(secondModel))
        assertTrue(secondModel.languageEqual(firstModel))
    }

    @Test
    fun `Node without reference to own parent if root`() {
        val a = Activity("A")
        with(processTree { Sequence(a) }) {
            assertNull(root!!.parent)
            assertEquals(toString(), "→(A)")
        }
    }

    @Test
    fun `Node with reference to own parent`() {
        val a = Activity("A")
        val b = Activity("B")
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
                Activity("A")
            )
        }

        with(model.root as RedoLoop) {
            assertEquals(children.size, 2)

            with(children[0] as SilentActivity) {
                assertTrue(children.isEmpty())
            }

            with(children[1] as Activity) {
                assertEquals(name, "A")
                assertTrue(children.isEmpty())
            }
        }

        assertEquals(model.toString(), "⟲(τ,A)")
    }

    @Test
    fun `Model with activity as root - only activity in model`() {
        val model = processTree {
            Activity("A")
        }

        with(model.root as Activity) {
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
                Activity("A"),
                Exclusive(
                    Parallel(
                        Activity("B"),
                        Activity("C")
                    ),
                    Activity("E")
                ),
                Activity("D")
            )
        }

        assertEquals(model.toString(), "→(A,×(∧(B,C),E),D)")

        with(model.root!!) {
            assert(this is Sequence)
            assertEquals(children.size, 3)

            with(children[0] as Activity) {
                assertEquals(name, "A")
                assertTrue(children.isEmpty())
            }

            with(children[1]) {
                assert(this is Exclusive)
                assertEquals(children.size, 2)

                with(children[0]) {
                    assert(this is Parallel)
                    assertEquals(children.size, 2)

                    with(children[0] as Activity) {
                        assertEquals(name, "B")
                        assertTrue(children.isEmpty())
                    }

                    with(children[1] as Activity) {
                        assertEquals(name, "C")
                        assertTrue(children.isEmpty())
                    }
                }

                with(children[1] as Activity) {
                    assertEquals(name, "E")
                    assertTrue(children.isEmpty())
                }
            }

            with(children[2] as Activity) {
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
                Activity("A"),
                RedoLoop(
                    Parallel(
                        Activity("B"),
                        Activity("C")
                    ),
                    Sequence(
                        Activity("E"),
                        Activity("F")
                    )
                ),
                Activity("D")
            )
        }

        assertEquals(model.toString(), "→(A,⟲(∧(B,C),→(E,F)),D)")

        with(model.root!!) {
            assert(this is Sequence)
            assertEquals(children.size, 3)

            with(children[0] as Activity) {
                assertEquals(name, "A")
                assertTrue(children.isEmpty())
            }

            with(children[1]) {
                assert(this is RedoLoop)
                assertEquals(children.size, 2)

                with(children[0]) {
                    assert(this is Parallel)
                    assertEquals(children.size, 2)

                    with(children[0] as Activity) {
                        assertEquals(name, "B")
                        assertTrue(children.isEmpty())
                    }

                    with(children[1] as Activity) {
                        assertEquals(name, "C")
                        assertTrue(children.isEmpty())
                    }
                }

                with(children[1]) {
                    assert(this is Sequence)
                    assertEquals(children.size, 2)

                    with(children[0] as Activity) {
                        assertEquals(name, "E")
                        assertTrue(children.isEmpty())
                    }

                    with(children[1] as Activity) {
                        assertEquals(name, "F")
                        assertTrue(children.isEmpty())
                    }
                }
            }

            with(children[2] as Activity) {
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
                Activity("A"),
                RedoLoop(
                    Sequence(
                        Parallel(
                            Exclusive(
                                Activity("B"),
                                Activity("C")
                            ),
                            Activity("D")
                        ),
                        Activity("E")
                    ),
                    Activity("F")
                ),
                Exclusive(
                    Activity("G"),
                    Activity("H")
                )
            )
        }

        assertEquals(model.toString(), "→(A,⟲(→(∧(×(B,C),D),E),F),×(G,H))")

        with(model.root!!) {
            assert(this is Sequence)
            assertEquals(children.size, 3)

            with(children[0] as Activity) {
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

                            with(children[0] as Activity) {
                                assertEquals(name, "B")
                                assertTrue(children.isEmpty())
                            }

                            with(children[1] as Activity) {
                                assertEquals(name, "C")
                                assertTrue(children.isEmpty())
                            }
                        }

                        with(children[1] as Activity) {
                            assertEquals(name, "D")
                            assertTrue(children.isEmpty())
                        }
                    }

                    with(children[1] as Activity) {
                        assertEquals(name, "E")
                        assertTrue(children.isEmpty())
                    }
                }

                with(children[1] as Activity) {
                    assertEquals(name, "F")
                    assertTrue(children.isEmpty())
                }
            }

            with(children[2]) {
                assert(this is Exclusive)
                assertEquals(children.size, 2)

                with(children[0] as Activity) {
                    assertEquals(name, "G")
                    assertTrue(children.isEmpty())
                }

                with(children[1] as Activity) {
                    assertEquals(name, "H")
                    assertTrue(children.isEmpty())
                }
            }
        }
    }

    @Test
    fun `start nodes example from PM page 82`() {
        assertEquals(setOf(SilentActivity()), processTree { SilentActivity() }.startActivities.toSet())
        assertEquals(setOf(Activity("a")), processTree { Activity("a") }.startActivities.toSet())
        assertEquals(
            setOf(Activity("a")),
            processTree { Sequence(Activity("a"), Activity("b"), Activity("c")) }.startActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a"), Activity("b"), Activity("c")),
            processTree { Exclusive(Activity("a"), Activity("b"), Activity("c")) }.startActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a"), Activity("b"), Activity("c")),
            processTree { Parallel(Activity("a"), Activity("b"), Activity("c")) }.startActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a")),
            processTree { RedoLoop(Activity("a"), Activity("b"), Activity("c")) }.startActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a")),
            processTree {
                Sequence(
                    Activity("a"),
                    Exclusive(Activity("b"), Activity("c")),
                    Parallel(Activity("a"), Activity("a"))
                )
            }.startActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a"), Activity("c"), SilentActivity()),
            processTree {
                Exclusive(
                    SilentActivity(),
                    Activity("a"),
                    SilentActivity(),
                    Sequence(
                        SilentActivity(),
                        Activity("b")
                    ),
                    Parallel(
                        Activity("c"),
                        SilentActivity()
                    )
                )
            }.startActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a")),
            processTree {
                RedoLoop(
                    Activity("a"),
                    SilentActivity(),
                    Activity("c")
                )
            }.startActivities.toSet()
        )
    }

    @Test
    fun `end nodes example from PM page 82`() {
        assertEquals(setOf(SilentActivity()), processTree { SilentActivity() }.endActivities.toSet())
        assertEquals(setOf(Activity("a")), processTree { Activity("a") }.endActivities.toSet())
        assertEquals(
            setOf(Activity("c")),
            processTree { Sequence(Activity("a"), Activity("b"), Activity("c")) }.endActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a"), Activity("b"), Activity("c")),
            processTree { Exclusive(Activity("a"), Activity("b"), Activity("c")) }.endActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a"), Activity("b"), Activity("c")),
            processTree { Parallel(Activity("a"), Activity("b"), Activity("c")) }.endActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a")),
            processTree { RedoLoop(Activity("a"), Activity("b"), Activity("c")) }.endActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a")),
            processTree {
                Sequence(
                    Activity("a"),
                    Exclusive(Activity("b"), Activity("c")),
                    Parallel(Activity("a"), Activity("a"))
                )
            }.endActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a"), Activity("b"), Activity("c"), SilentActivity()),
            processTree {
                Exclusive(
                    SilentActivity(),
                    Activity("a"),
                    SilentActivity(),
                    Sequence(
                        SilentActivity(),
                        Activity("b")
                    ),
                    Parallel(
                        Activity("c"),
                        SilentActivity()
                    )
                )
            }.endActivities.toSet()
        )
        assertEquals(
            setOf(Activity("a")),
            processTree {
                RedoLoop(
                    Activity("a"),
                    SilentActivity(),
                    Activity("c")
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
        val a1 = Activity("a1")
        val a2 = Activity("a2")
        val a3 = Activity("a3")
        val b1 = Activity("b1")
        val b2 = Activity("b2")
        val b3 = Activity("b3")
        val c1 = Activity("c1")
        val c2 = Activity("c2")
        val c3 = Activity("c3")
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
        val a = Activity("a")
        val b = Activity("b")
        val c = Activity("c")
        val d = Activity("d")
        val e = Activity("e")
        val f = Activity("f")
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