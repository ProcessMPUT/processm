package processm.conformance.rca

import processm.conformance.alignment
import kotlin.test.Test
import kotlin.test.assertEquals


class RootCauseAnalysisTest {

    @Test
    fun `if different people then problem`() {
        val neg = listOf(
            alignment {
                "a" with ("person" to "A") executing "a"
                "b" with ("person" to "A") executing "b"
                "c" executing "c"
                "d" executing "d"
            },
            alignment {
                "a" with ("person" to "B") executing "a"
                "b" with ("person" to "B") executing "b"
                "c" executing "c"
                "d" executing "d"
            }
        )
        val pos = listOf(
            alignment {
                "a" with ("person" to "A") executing "a"
                null executing "c"
                "b" with ("person" to "B") executing "b"
                "d" executing "d"
                "c" executing null
            },
            alignment {
                "a" with ("person" to "B") executing "a"
                null executing "c"
                "b" with ("person" to "A") executing "b"
                "d" executing "d"
                "c" executing null
            }
        )
        val trees = RootCauseAnalysisDataSet(pos, neg).explain().toList()
        assertEquals(2, trees.size)
        with(trees[0]) {
            assertEquals(2, depth)
        }
        with(trees[1]) {
            assertEquals(1, depth)
        }
        for (tree in trees) {
            println(tree.toMultilineString())
            println("==================")
        }
    }

    @Test
    fun `if no person then problem`() {
        val neg = listOf(
            alignment {
                "a" with ("person" to "A") executing "a"
                "b" with ("person" to "A") executing "b"
                "c" executing "c"
                "d" executing "d"
            },
            alignment {
                "a" with ("person" to "B") executing "a"
                "b" with ("person" to "B") executing "b"
                "c" executing "c"
                "d" executing "d"
            }
        )
        val pos = listOf(
            alignment {
                "a" executing "a"
                null executing "c"
                "b" with ("person" to "B") executing "b"
                "d" executing "d"
                "c" executing null
            },
            alignment {
                "a" with ("person" to "B") executing "a"
                null executing "c"
                "b" executing "b"
                "d" executing "d"
                "c" executing null
            }
        )
        val trees = RootCauseAnalysisDataSet(pos, neg).explain().toList()
        assertEquals(3, trees.size)
        with(trees[0]) {
            assertEquals(3, depth)
        }
        with(trees[1]) {
            assertEquals(2, depth)
        }
        with(trees[2]) {
            assertEquals(1, depth)
        }
    }

    @Test
    fun `if no length then problem`() {
        val neg = listOf(
            alignment {
                "a" with ("length" to 1) executing "a"
                "b" with ("length" to 1) executing "b"
                "c" executing "c"
                "d" executing "d"
            },
            alignment {
                "a" with ("length" to 2) executing "a"
                "b" with ("length" to 2) executing "b"
                "c" executing "c"
                "d" executing "d"
            }
        )
        val pos = listOf(
            alignment {
                "a" executing "a"
                null executing "c"
                "b" with ("length" to 2) executing "b"
                "d" executing "d"
                "c" executing null
            },
            alignment {
                "a" with ("length" to 1) executing "a"
                null executing "c"
                "b" executing "b"
                "d" executing "d"
                "c" executing null
            }
        )
        val trees = RootCauseAnalysisDataSet(pos, neg).explain().toList()
        assertEquals(2, trees.size)
        with(trees[0]) {
            assertEquals(2, depth)
        }
        with(trees[1]) {
            assertEquals(1, depth)
        }
        for (tree in trees) {
            println(tree.toMultilineString())
            println("==================")
        }
    }
}