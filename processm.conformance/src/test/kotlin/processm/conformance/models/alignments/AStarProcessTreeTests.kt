package processm.conformance.models.alignments

import processm.conformance.alignment
import processm.core.log.Helpers.logFromString
import processm.core.models.processtree.ProcessTrees.azFlower
import processm.core.models.processtree.ProcessTrees.fig727
import processm.core.models.processtree.ProcessTrees.fig729
import processm.core.models.processtree.ProcessTrees.parallelDecisionsInLoop
import processm.core.models.processtree.ProcessTrees.parallelFlowers
import processm.helpers.mapToSet
import processm.logging.loggedScope
import kotlin.test.Test
import kotlin.test.assertEquals

class AStarProcessTreeTests {

    @Test
    fun `PM book Fig 7 27 conforming log`() = loggedScope { logger ->
        val log = logFromString(
            """
                A B D E H
                A D C E G
                A C D E F B D E G
                A D B E H
                A C D E F D C E F C D E H
                A C D E G
                A D C E F C D E F D B E F D C E F C D E F D B E H
                A B D E F C D E F D B E F D C E F B D E F C D E F D B E F D C E F B D E F C D E F D B E F D C E F B D E F C D E F D B E F D C E F B D E F C D E F D B E F D C E H
                """
        )

        val astar = AStar(fig727)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            logger.info("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.size)
            for (step in alignment.steps)
                assertEquals(step.logMove!!.conceptName, step.modelMove!!.name)
        }
    }

    @Test
    fun `PM book Fig 7 27 non-conforming log`() = loggedScope { logger ->
        val log = logFromString(
            """
                A E B D H
                D A C E G
                D A D C E G
                A C D E F D E G
                A D B E G H
                A C D Z E F D C E F C D E H
                A C E G
                A D C E F C D E F D B F D C E F C D E F D B E H
                H E D C A
                A B C D E F B C D E F D B E H
                A B D E F C D E F C D E F D B C E F D C B E F
                """
        )

        val expected = arrayOf(
            arrayOf( // A E B D H
                alignment { // [A, log only: E, B, D, model only: E, H]
                    "A" executing "A"
                    "E" executing null
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    null executing ("E" to listOf("B", "D"))
                    "H" executing ("H" to listOf("E"))
                    cost = 2
                }
            ),
            arrayOf( // D A C E G
                alignment {// [log only: D, A, C, model only: D, E, G]
                    "D" executing null
                    "A" executing "A"
                    "C" executing ("C" to listOf("A"))
                    null executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("C", "D"))
                    "G" executing ("G" to listOf("E"))
                    cost = 2
                },
                alignment {//  [log only: D, A, model only: D, C, E, G]
                    "D" executing null
                    "A" executing "A"
                    null executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    "E" executing ("E" to listOf("C", "D"))
                    "G" executing ("G" to listOf("E"))
                    cost = 2
                },
                alignment {
                    null executing "A"
                    "D" executing ("D" to listOf("A"))
                    "A" executing null
                    "C" executing ("C" to listOf("A"))
                    "E" executing ("E" to listOf("C", "D"))
                    "G" executing ("G" to listOf("E"))
                    cost = 2
                }
            ),
            arrayOf( // D A D C E G
                alignment {// [model only: D, A, D, C, E, G]
                    "D" executing null
                    "A" executing "A"
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    "E" executing ("E" to listOf("C", "D"))
                    "G" executing ("G" to listOf("E"))
                    cost = 1
                }
            ),
            arrayOf(
                // A C D E F D E G
                alignment {// [A, C, D, E, F, D, model only: B, E, G]
                    "A" executing "A"
                    "C" executing ("C" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    null executing ("B" to listOf("F"))
                    "E" executing ("E" to listOf("B", "D"))
                    "G" executing ("G" to listOf("E"))
                    cost = 1
                },
                alignment {// [A, C, D, E, F, D, model only: C, E, G]
                    "A" executing "A"
                    "C" executing ("C" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    null executing ("C" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "G" executing ("G" to listOf("E"))
                    cost = 1
                },
                alignment {// [A, C, D, E, F, model only: B, D, E, G]
                    "A" executing "A"
                    "C" executing ("C" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    null executing ("B" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("B", "D"))
                    "G" executing ("G" to listOf("E"))
                    cost = 1
                },
                alignment {// [A, C, D, E, F, model only: C, D, E, G]
                    "A" executing "A"
                    "C" executing ("C" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    null executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "G" executing ("G" to listOf("E"))
                    cost = 1
                },
            ),
            arrayOf(
                // A D B E G H
                alignment {// [A, D, B, E, G, log only: H]
                    "A" executing "A"
                    "D" executing ("D" to listOf("A"))
                    "B" executing ("B" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "G" executing ("G" to listOf("E"))
                    "H" executing null
                    cost = 1
                },
                alignment {// [A, D, B, E, log only: G, H]
                    "A" executing "A"
                    "D" executing ("D" to listOf("A"))
                    "B" executing ("B" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "G" executing null
                    "H" executing ("H" to listOf("E"))
                    cost = 1
                },
            ),
            arrayOf( // A C D Z E F D C E F C D E H
                alignment {// [A, C, D, log only: Z, E, F, D, C, E, F, C, D, E, H]
                    "A" executing "A"
                    "C" executing ("C" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "Z" executing null
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing ("C" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "H" executing ("H" to listOf("E"))
                    cost = 1
                }
            ),
            arrayOf(
                // A C E G
                alignment {// [A, C, model only: D, E, G]
                    "A" executing "A"
                    "C" executing ("C" to listOf("A"))
                    null executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("C", "D"))
                    "G" executing ("G" to listOf("E"))
                    cost = 1
                },
                alignment {// [A, model only: D, C, E, G]
                    "A" executing "A"
                    null executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    "E" executing ("E" to listOf("C", "D"))
                    "G" executing ("G" to listOf("E"))
                    cost = 1
                },
            ),
            arrayOf(
                // A D C E F C D E F D B F D C E F C D E F D B E H
                alignment {// [A, D, C, E, F, C, D, E, F, D, B, model only: E, F, D, C, E, F, C, D, E, F, D, B, E, H]
                    "A" executing "A"
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing ("B" to listOf("F"))
                    null executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing ("C" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing ("B" to listOf("F"))
                    "E" executing ("E" to listOf("B", "D"))
                    "H" executing ("H" to listOf("E"))
                    cost = 1
                }),
            arrayOf(
                // H E D C A
                alignment {// [log: H, log: E, model: A, D, C, log: A, model: E, model: G]
                    "H" executing null
                    "E" executing null
                    null executing "A"
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    "A" executing null
                    null executing ("E" to listOf("C", "D"))
                    null executing ("G" to listOf("E"))
                    cost = 6
                },
                alignment {// [log: H, model: A, log: E, D, C, log: A, model: E, model: G]
                    "H" executing null
                    null executing "A"
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    "A" executing null
                    null executing ("E" to listOf("C", "D"))
                    null executing ("G" to listOf("E"))
                    cost = 6
                },
                alignment {// [model: A, log: H, log: E, D, C, log: A, model: E, model: G]
                    null executing "A"
                    "H" executing null
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    "A" executing null
                    null executing ("E" to listOf("C", "D"))
                    null executing ("G" to listOf("E"))
                    cost = 6
                },
                alignment {// [log: H, log: E, model: A, D, C, model: E, log: A, model: G]
                    "H" executing null
                    "E" executing null
                    null executing "A"
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    "A" executing null
                    null executing ("G" to listOf("E"))
                    cost = 6
                },
                alignment {// [log: H, model: A, log: E, D, C, model: E, log: A, model: G]
                    "H" executing null
                    null executing "A"
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    "A" executing null
                    null executing ("G" to listOf("E"))
                    cost = 6
                },
                alignment {// [model: A, log: H, log: E, D, C, model: E, log: A, model: G]
                    null executing "A"
                    "H" executing null
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    "A" executing null
                    null executing ("G" to listOf("E"))
                    cost = 6
                },
                alignment {// [log: H, log: E, model: A, D, C, model: E, model: G, log: A]
                    "H" executing null
                    "E" executing null
                    null executing "A"
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    null executing ("G" to listOf("E"))
                    "A" executing null
                    cost = 6
                },
                alignment {// [log: H, model: A, log: E, D, C, model: E, model: G, log: A]
                    "H" executing null
                    null executing "A"
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    null executing ("G" to listOf("E"))
                    "A" executing null
                    cost = 6
                },
                alignment {// [model: A, log: H, log: E, D, C, model: E, model: G, log: A]
                    null executing "A"
                    "H" executing null
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    null executing ("G" to listOf("E"))
                    "A" executing null
                    cost = 6
                },
                alignment {// [log: H, log: E, model: A, D, C, log: A, model: E, model: H]
                    "H" executing null
                    "E" executing null
                    null executing "A"
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    "A" executing null
                    null executing ("E" to listOf("C", "D"))
                    null executing ("H" to listOf("E"))
                    cost = 6
                },
                alignment {// [log: H, model: A, log: E, D, C, log: A, model: E, model: G]
                    "H" executing null
                    null executing "A"
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    "A" executing null
                    null executing ("E" to listOf("C", "D"))
                    null executing ("H" to listOf("E"))
                    cost = 6
                },
                alignment {// [model: A, log: H, log: E, D, C, log: A, model: E, model: H]
                    null executing "A"
                    "H" executing null
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    "A" executing null
                    null executing ("E" to listOf("C", "D"))
                    null executing ("H" to listOf("E"))
                    cost = 6
                },
                alignment {// [log: H, log: E, model: A, D, C, model: E, log: A, model: H]
                    "H" executing null
                    "E" executing null
                    null executing "A"
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    "A" executing null
                    null executing ("H" to listOf("E"))
                    cost = 6
                },
                alignment {// [log: H, model: A, log: E, D, C, model: E, log: A, model: H]
                    "H" executing null
                    null executing "A"
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    "A" executing null
                    null executing ("H" to listOf("E"))
                    cost = 6
                },
                alignment {// [model: A, log: H, log: E, D, C, model: E, log: A, model: H]
                    null executing "A"
                    "H" executing null
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    "A" executing null
                    null executing ("H" to listOf("E"))
                    cost = 6
                },
                alignment {// [log: H, log: E, model: A, D, C, model: E, model: H, log: A]
                    "H" executing null
                    "E" executing null
                    null executing "A"
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    null executing ("H" to listOf("E"))
                    "A" executing null
                    cost = 6
                },
                alignment {// [log: H, model: A, log: E, D, C, model: E, model: H, log: A]
                    "H" executing null
                    null executing "A"
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    null executing ("H" to listOf("E"))
                    "A" executing null
                    cost = 6
                },
                alignment {// [model: A, log: H, log: E, D, C, model: E, model: H, log: A]
                    null executing "A"
                    "H" executing null
                    "E" executing null
                    "D" executing ("D" to listOf("A"))
                    "C" executing ("C" to listOf("A"))
                    null executing ("E" to listOf("C", "D"))
                    null executing ("H" to listOf("E"))
                    "A" executing null
                    cost = 6
                },
            ),
            arrayOf(
                // A B C D E F B C D E F D B E H
                alignment {
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "C" executing null
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "B" executing ("B" to listOf("F"))
                    "C" executing null
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing ("B" to listOf("F"))
                    "E" executing ("E" to listOf("B", "D"))
                    "H" executing ("H" to listOf("E"))
                    cost = 2
                }
            ),
            arrayOf(
                // A B D E F C D E F C D E F D B C E F D C B E F
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, B, log: C, E, F, D, C, log: B, E, log: F, model: G]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing ("B" to listOf("F"))
                    "C" executing null
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing ("C" to listOf("F"))
                    "B" executing null
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing null
                    null executing ("G" to listOf("E"))
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, log: B, C, E, F, D, C, log: B, E, log: F, model: G]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing null
                    "C" executing ("C" to listOf("F"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing ("C" to listOf("F"))
                    "B" executing null
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing null
                    null executing ("G" to listOf("E"))
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, B, log: C, E, F, D, log: C, B, E, log: F, model: G]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing ("B" to listOf("F"))
                    "C" executing null
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing null
                    "B" executing ("B" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing null
                    null executing ("G" to listOf("E"))
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, B, log: C, E, F, D, C, log: B, E, log: F, model: H]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing ("B" to listOf("F"))
                    "C" executing null
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing ("C" to listOf("F"))
                    "B" executing null
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing null
                    null executing ("H" to listOf("E"))
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, log: B, C, E, F, D, log: C, B, E, log: F, model: G]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing null
                    "C" executing ("C" to listOf("F"))
                    "E" executing ("E" to listOf("B", "F"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing null
                    "B" executing ("B" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing null
                    null executing ("G" to listOf("E"))
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, log: B, C, E, F, D, C, log: B, E, log: F, model: H]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing null
                    "C" executing ("C" to listOf("F"))
                    "E" executing ("E" to listOf("B", "F"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing ("C" to listOf("F"))
                    "B" executing null
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing null
                    null executing ("H" to listOf("E"))
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, B, log: C, E, F, D, log: C, B, E, log: F, model: H]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing ("B" to listOf("F"))
                    "C" executing null
                    "E" executing ("E" to listOf("B", "F"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing null
                    "B" executing ("B" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing null
                    null executing ("H" to listOf("E"))
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, log: B, C, E, F, D, log: C, B, E, log: F, model: H]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing null
                    "C" executing ("C" to listOf("F"))
                    "E" executing ("E" to listOf("B", "F"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing null
                    "B" executing ("B" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing null
                    null executing ("H" to listOf("E"))
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, B, log: C, E, F, D, C, log: B, E, model: G, log: F]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing ("B" to listOf("F"))
                    "C" executing null
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing ("C" to listOf("F"))
                    "B" executing null
                    "E" executing ("E" to listOf("C", "D"))
                    null executing ("G" to listOf("E"))
                    "F" executing null
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, log: B, C, E, F, D, C, log: B, E, model: G, log: F]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing null
                    "C" executing ("C" to listOf("F"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing ("C" to listOf("F"))
                    "B" executing null
                    "E" executing ("E" to listOf("C", "D"))
                    null executing ("G" to listOf("E"))
                    "F" executing null
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, B, log: C, E, F, D, log: C, B, E, model: G, log: F]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing ("B" to listOf("F"))
                    "C" executing null
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing null
                    "B" executing ("B" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    null executing ("G" to listOf("E"))
                    "F" executing null
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, B, log: C, E, F, D, C, log: B, E, model: H, log: F]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing ("B" to listOf("F"))
                    "C" executing null
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing ("C" to listOf("F"))
                    "B" executing null
                    "E" executing ("E" to listOf("C", "D"))
                    null executing ("H" to listOf("E"))
                    "F" executing null
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, log: B, C, E, F, D, log: C, B, E, model: G, log: F]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing null
                    "C" executing ("C" to listOf("F"))
                    "E" executing ("E" to listOf("B", "F"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing null
                    "B" executing ("B" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    null executing ("G" to listOf("E"))
                    "F" executing null
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, log: B, C, E, F, D, C, log: B, E, model: H, log: F]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing null
                    "C" executing ("C" to listOf("F"))
                    "E" executing ("E" to listOf("B", "F"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing ("C" to listOf("F"))
                    "B" executing null
                    "E" executing ("E" to listOf("C", "D"))
                    null executing ("H" to listOf("E"))
                    "F" executing null
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, B, log: C, E, F, D, log: C, B, E, model: H, log: F]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing ("B" to listOf("F"))
                    "C" executing null
                    "E" executing ("E" to listOf("B", "F"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing null
                    "B" executing ("B" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    null executing ("H" to listOf("E"))
                    "F" executing null
                    cost = 4
                },
                alignment {// [A, B, D, E, F, C, D, E, F, C, D, E, F, D, log: B, C, E, F, D, log: C, B, E, model: H, log: F]
                    "A" executing "A"
                    "B" executing ("B" to listOf("A"))
                    "D" executing ("D" to listOf("A"))
                    "E" executing ("E" to listOf("B", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "C" executing ("C" to listOf("F"))
                    "D" executing ("D" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "B" executing null
                    "C" executing ("C" to listOf("F"))
                    "E" executing ("E" to listOf("B", "F"))
                    "F" executing ("F" to listOf("E"))
                    "D" executing ("D" to listOf("F"))
                    "C" executing null
                    "B" executing ("B" to listOf("F"))
                    "E" executing ("E" to listOf("C", "D"))
                    null executing ("H" to listOf("E"))
                    "F" executing null
                    cost = 4
                },
            ),
        )

        val astar = AStar(fig727)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            logger.info("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(trace.events.count(), alignment.steps.count { it.logMove !== null })

            val results = expected[i].map { expected ->
                runCatching {
                    assertEquals(expected.cost, alignment.cost)
                    assertEquals(expected.steps.size, alignment.steps.size)
                    for ((exp, act) in expected.steps zip alignment.steps) {
                        assertEquals(exp.type, act.type, "exp: ${exp}\nact: ${act}")
                        assertEquals(exp.logMove?.conceptName, act.logMove?.conceptName)
                        assertEquals(exp.modelMove?.name, act.modelMove?.name)
                        assertEquals(exp.modelCause.mapToSet { it.name }, act.modelCause.mapToSet { it.name })
                    }
                }
            }

            if (results.none { it.isSuccess }) {
                results.forEach { it.getOrThrow() }
            }
        }
    }

    @Test
    fun `Flower process tree`() {
        val log = logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A A A A A A A A A A A A A A A A A A A A A A A A A A
                Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z
                A
                Z
            """
        )

        val astar = AStar(azFlower)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count() * 2 + 1, alignment.steps.size)
        }
    }

    @Test
    fun `Parallel flower models`() {
        val log = logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A A A A A A A A A A A A A A A A A A A A A A A A A A
                Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z
                A
                Z
                Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z A A A A A A A A A A A A A A A A A A A A A A A A A A
                Z Z Z Z Z Z Z Z Z Z Z Z Z Y Z Z Z Z Z Z Z Z Z Z Z Z A A A A A A A A A A A A B A A A A A A A A A A A A A
            """
        )

        val astar = AStar(parallelFlowers)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count() * 2 + 2, alignment.steps.size)
        }
    }

    @Test
    fun `Parallel flower models non-conforming log`() {
        val log = logFromString(
            """
                1 A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z 2 Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A A 3 A A A A A A A A A A A A A A A A A A A A A A A A
                Z Z Z 4 Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z
                A 5
                Z 6
                Z Z Z 7 A A A
            """
        )

        val astar = AStar(parallelFlowers)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(1, alignment.cost)
            assertEquals(trace.events.count() * 2 + 1, alignment.steps.size)
        }
    }

    @Test
    fun `Parallel decisions in loop process tree`() {
        val log = logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A Z Z A A Z Z A A Z
                A Z
                Z A
            """
        )

        val astar = AStar(parallelDecisionsInLoop)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
        }
    }

    @Test
    fun `Parallel decisions in loop non-conforming log`() {
        val log = logFromString(
            """
                A A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z Z Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A A Z Z Z
                Z Z A A A
                A Y Z B
                Z B A Y
                A A A
                Z Z Z
                Z Z A A A A Z Z
            """
        )

        val expectedCosts = listOf(
            1,
            1,
            3,
            3,
            2,
            2,
            3,
            3,
            4
        )

        val astar = AStar(parallelDecisionsInLoop)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
        }
    }

    @Test
    fun `PM book Fig 7 29 conforming log`() {
        val log = logFromString(
            """
                A C E G
                A E C G
                B D F G
                B F D G
                """
        )

        val astar = AStar(fig729)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.size)
            for (step in alignment.steps)
                assertEquals(step.logMove!!.conceptName, step.modelMove!!.name)
        }
    }

    @Test
    fun `PM book Fig 7 29 non-conforming log`() {
        val log = logFromString(
            """
                D F B G E C A
                """
        )

        val expectedCosts = listOf(
            5,
        )

        val astar = AStar(fig729)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
        }
    }
}
