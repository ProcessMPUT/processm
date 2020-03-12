package processm.core.models.processtree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    @Test
    fun `Node without reference to own parent if root`() {
        val a = Activity("A")
        with(processTree { Sequence(a) }) {
            assertNull(root!!.parent)
        }
    }

    @Test
    fun `Node with reference to own parent`() {
        val a = Activity("A")
        val b = Activity("B")
        with(processTree { Sequence(a, b) }.root!!) {
            assertNull(parent)

            children.forEach { childrenNode ->
                assertEquals(childrenNode.parent, this)
            }
        }
    }

    @Test
    fun `Model without activities`() {
        val model = processTree { null }
        assertNull(model.root)
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
}