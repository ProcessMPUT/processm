package processm.helpers

import processm.logging.logger
import kotlin.system.measureTimeMillis
import kotlin.test.*

class AllSubsetsTests {
    @Test
    fun subsets() {
        val validPowerset = setOf(
            setOf(),
            setOf("a"), setOf("b"), setOf("c"),
            setOf("a", "b"), setOf("a", "c"), setOf("c", "b"),
            setOf("a", "b", "c")
        )
        val calculatedPowerset = setOf("a", "b", "c").allSubsets()

        // test PowerSetImpl<T>.equals()
        assertEquals(validPowerset, calculatedPowerset)
        assertEquals(calculatedPowerset, validPowerset)
        assertEquals(calculatedPowerset, calculatedPowerset)

        // test PowerSetImpl<T>.hashCode()
        assertEquals(validPowerset.hashCode(), calculatedPowerset.hashCode())
        assertEquals(calculatedPowerset.hashCode(), calculatedPowerset.hashCode())


        // test PowerSetImpl<T>.contains()
        assertTrue(emptySet() in calculatedPowerset)
        assertFalse(setOf("a", "b", "c", "d") in calculatedPowerset)
        for (validSubset in validPowerset) {
            // test PowerSetImpl<T>.contains()
            assertTrue(validSubset in calculatedPowerset)

            // test Subset<T>.contains() and Subset<T>.containsAll():
            assertTrue(calculatedPowerset.any { calculatedSubset -> calculatedSubset.containsAll(validSubset) })

            // test Subset<T>.indexOf()
            val index = calculatedPowerset.indexOf(validSubset)
            assertTrue(index >= 0)
            assertEquals(index, calculatedPowerset.lastIndexOf(validSubset))

            // test Subset<T>.equals()
            val calculatedSubset = calculatedPowerset[index]
            assertEquals(validSubset, calculatedSubset)
            assertEquals(calculatedSubset, calculatedSubset)

            // test Subset<T>.hashCode()
            assertEquals(validSubset.hashCode(), calculatedSubset.hashCode())
            assertEquals(calculatedSubset.hashCode(), calculatedSubset.hashCode())
        }
    }

    @Test
    fun subsetsWithoutEmpty() {
        val validPowerset = setOf(
            setOf("a"), setOf("b"), setOf("c"),
            setOf("a", "b"), setOf("a", "c"), setOf("c", "b"),
            setOf("a", "b", "c")
        )
        val calculatedPowerset = setOf("a", "b", "c").allSubsets(true)

        // test PowerSetImpl<T>.equals()
        assertEquals(validPowerset, calculatedPowerset)
        assertEquals(calculatedPowerset, validPowerset)
        assertEquals(calculatedPowerset, calculatedPowerset)

        // test PowerSetImpl<T>.hashCode()
        assertEquals(validPowerset.hashCode(), calculatedPowerset.hashCode())
        assertEquals(calculatedPowerset.hashCode(), calculatedPowerset.hashCode())


        // test PowerSetImpl<T>.contains()
        assertFalse(emptySet() in calculatedPowerset)
        assertFalse(setOf("a", "b", "c", "d") in calculatedPowerset)
        for (validSubset in validPowerset) {
            // test PowerSetImpl<T>.contains()
            assertTrue(validSubset in calculatedPowerset)

            // test Subset<T>.contains() and Subset<T>.containsAll():
            assertTrue(calculatedPowerset.any { calculatedSubset -> calculatedSubset.containsAll(validSubset) })

            // test Subset<T>.indexOf()
            val index = calculatedPowerset.indexOf(validSubset)
            assertTrue(index >= 0)
            assertEquals(index, calculatedPowerset.lastIndexOf(validSubset))

            // test Subset<T>.equals()
            val calculatedSubset = calculatedPowerset[index]
            assertEquals(validSubset, calculatedSubset)
            assertEquals(calculatedSubset, calculatedSubset)

            // test Subset<T>.hashCode()
            assertEquals(validSubset.hashCode(), calculatedSubset.hashCode())
            assertEquals(calculatedSubset.hashCode(), calculatedSubset.hashCode())
        }
    }

    @Ignore("Intended for manual execution")
    @Test
    fun `allSubsets performance`() {
        val list = "ABCDEFGHIJKLMNOPQRSTUWVXYZ".toList()
        // warm up
        for (subset in list.allSubsets(false)) {
            for (item in subset) {
                // nothing
            }
        }
        measureTimeMillis {
            for (subset in list.allSubsets(false)) {
                for (item in subset) {
                    // nothing
                }
            }
        }.also { logger().info("Calculated power set with empty subset in $it ms.") }

        measureTimeMillis {
            for (subset in list.allSubsets(true)) {
                for (item in subset) {
                    // nothing
                }
            }
        }.also { logger().info("Calculated power set without empty subset in $it ms.") }
    }

    @Test
    fun `subsets of empty`() {
        assertEquals(
            setOf(setOf()),
            setOf<Int>().allSubsets().mapToSet { it.toSet() }
        )
    }

    @Test
    fun `subsets of empty filtered for non-empty`() {
        assertEquals(
            setOf(),
            setOf<Int>().allSubsets(true).mapToSet { it.toSet() }
        )
    }

    @Test
    fun permutations() {
        assertEquals(
            setOf(
                listOf("a", "b", "c"),
                listOf("a", "c", "b"),
                listOf("b", "a", "c"),
                listOf("b", "c", "a"),
                listOf("c", "a", "b"),
                listOf("c", "b", "a")
            ),
            listOf("a", "b", "c").allPermutations().toSet()
        )
    }

    @Test
    fun `all subsets up to size 3`() {
        val subsets = listOf("a", "b", "c", "d").allSubsetsUpToSize(3).toSet()
        assertEquals(
            setOf(
                setOf("a"),
                setOf("b"),
                setOf("c"),
                setOf("d"),
                setOf("a", "b"),
                setOf("a", "c"),
                setOf("a", "d"),
                setOf("b", "c"),
                setOf("b", "d"),
                setOf("c", "d"),
                setOf("a", "b", "c"),
                setOf("a", "b", "d"),
                setOf("a", "c", "d"),
                setOf("b", "c", "d")
            ), subsets
        )
    }

    @Test
    fun `all subsets up to size 2`() {
        val subsets = listOf("a", "b", "c", "d").allSubsetsUpToSize(2).toSet()
        assertEquals(
            setOf(
                setOf("a"),
                setOf("b"),
                setOf("c"),
                setOf("d"),
                setOf("a", "b"),
                setOf("a", "c"),
                setOf("a", "d"),
                setOf("b", "c"),
                setOf("b", "d"),
                setOf("c", "d")
            ), subsets
        )
    }

    @Test
    fun `all subsets up to size 1`() {
        val subsets = listOf("a", "b", "c", "d").allSubsetsUpToSize(1).toSet()
        assertEquals(
            setOf(
                setOf("a"),
                setOf("b"),
                setOf("c"),
                setOf("d")
            ), subsets
        )
    }

    @Test
    fun `all subsets up to size 4`() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(list.allSubsets(true), list.allSubsetsUpToSize(4).toSet())
    }

    private fun testCount(n: Int) {
        val input = (0 until n).toList()
        assertEquals(n, input.size)
        val expected = 0 + //ignored empty subsets
                n +   // subsets of size 1
                n * (n - 1) / 2 //subsets of size 2
        assertEquals(expected, input.allSubsetsUpToSize(2).count())
    }

    @Test
    fun `count subsets up to size 2 of list of 40`() {
        testCount(40)
    }

    @Test
    fun `count subsets up to size 2 of list of 70`() {
        testCount(70)
    }

    @Test
    fun `count subsets up to size 2 of list of 300`() {
        testCount(300)
    }
}
