package processm.core.models.processtree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ModelTest {

    @Test
    fun `Model without activities`() {
        val model = Model()
        assertNull(model.root)
    }

    @Test
    fun `Model with activity as root - only activity in model`() {
        val model = Model(Activity("A"))

        with(model.root as Activity) {
            assertEquals(name, "A")
            assertTrue(children.isEmpty())
        }
    }

    @Test
    fun `Process Mining - Figure 7 point 20`() {
        // Log file: [{a,b,c,d}, {a,c,b,d}, {a,e,d}]
        val sequence = Sequence()
        val exclusive = ExclusiveChoice()
        val parallel = Parallel()

        sequence.childrenInternal.add(Activity("A"))
        sequence.childrenInternal.add(exclusive)
        sequence.childrenInternal.add(Activity("D"))

        exclusive.childrenInternal.add(parallel)
        exclusive.childrenInternal.add(Activity("E"))

        parallel.childrenInternal.add(Activity("B"))
        parallel.childrenInternal.add(Activity("C"))

        val model = Model(sequence)
        with(model.root!!) {
            assert(this is Sequence)
            assertEquals(children.size, 3)

            with(children[0] as Activity) {
                assertEquals(name, "A")
                assertTrue(children.isEmpty())
            }

            with(children[1]) {
                assert(this is ExclusiveChoice)
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
        val sequence = Sequence()
        val redoLoop = RedoLoop()
        val insideSequence = Sequence()
        val parallel = Parallel()

        sequence.childrenInternal.add(Activity("A"))
        sequence.childrenInternal.add(redoLoop)
        sequence.childrenInternal.add(Activity("D"))

        redoLoop.childrenInternal.add(parallel)
        redoLoop.childrenInternal.add(insideSequence)

        parallel.childrenInternal.add(Activity("B"))
        parallel.childrenInternal.add(Activity("C"))

        insideSequence.childrenInternal.add(Activity("E"))
        insideSequence.childrenInternal.add(Activity("F"))

        val model = Model(sequence)
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
        val sequence = Sequence()
        val redoLoop = RedoLoop()
        val exclusiveChoice = ExclusiveChoice()
        val insideSequence = Sequence()
        val parallel = Parallel()
        val insideExclusiveChoice = ExclusiveChoice()

        sequence.childrenInternal.add(Activity("A"))
        sequence.childrenInternal.add(redoLoop)
        sequence.childrenInternal.add(exclusiveChoice)

        redoLoop.childrenInternal.add(insideSequence)
        redoLoop.childrenInternal.add(Activity("F"))

        exclusiveChoice.childrenInternal.add(Activity("G"))
        exclusiveChoice.childrenInternal.add(Activity("H"))

        insideSequence.childrenInternal.add(parallel)
        insideSequence.childrenInternal.add(Activity("E"))

        parallel.childrenInternal.add(insideExclusiveChoice)
        parallel.childrenInternal.add(Activity("D"))

        insideExclusiveChoice.childrenInternal.add(Activity("B"))
        insideExclusiveChoice.childrenInternal.add(Activity("C"))

        val model = Model(sequence)
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
                            assert(this is ExclusiveChoice)
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
                assert(this is ExclusiveChoice)
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