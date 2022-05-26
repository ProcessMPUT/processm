package processm.conformance.rca.ml.spark

import processm.conformance.rca.Feature
import processm.conformance.rca.Label
import processm.conformance.rca.PropositionalSparseDataset
import processm.conformance.rca.ml.DecisionTreeModel
import processm.core.helpers.mapToSet
import processm.core.log.Helpers.assertDoubleEquals
import java.time.Instant
import kotlin.test.*

class SparkDecisionTreeClassifierTest {

    @Test
    fun `two features string irrelevant int relevant`() {
        val f1 = Feature("f1", Int::class)
        val f2 = Feature("f2", String::class)
        val dataset = PropositionalSparseDataset((0..17).map {
            mapOf(f1 to it, f2 to "10", Label to (it <= 10))
        })
        val cls = SparkDecisionTreeClassifier().fit(dataset)
        with(cls.root) {
            assertIs<DecisionTreeModel.InternalNode>(this)
            assertEquals(f1, split.feature)
            with(split) {
                assertIs<DecisionTreeModel.ContinuousSplit<Double>>(this)
                assertTrue { threshold > 10 }
                assertTrue { threshold < 11 }
            }
            with(left) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertTrue { decision }
            }
            with(right) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertFalse { decision }
            }
        }
    }

    @Test
    fun `two features string irrelevant long relevant`() {
        val f1 = Feature("f1", Long::class)
        val f2 = Feature("f2", String::class)
        val dataset = PropositionalSparseDataset((0L..17L).map {
            mapOf(f1 to it, f2 to "10", Label to (it <= 10))
        })
        val cls = SparkDecisionTreeClassifier().fit(dataset)
        with(cls.root) {
            assertIs<DecisionTreeModel.InternalNode>(this)
            assertEquals(f1, split.feature)
            with(split) {
                assertIs<DecisionTreeModel.ContinuousSplit<Double>>(this)
                assertTrue { threshold > 10 }
                assertTrue { threshold < 11 }
            }
            with(left) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertTrue { decision }
            }
            with(right) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertFalse { decision }
            }
        }
    }

    @Test
    fun `two features string relevant int irrelevant`() {
        val f1 = Feature("f1", String::class)
        val f2 = Feature("f2", Int::class)
        val dataset = PropositionalSparseDataset((0..17).map {
            mapOf(f1 to it.toString(), f2 to 10, Label to (it <= 10))
        })
        val cls = SparkDecisionTreeClassifier().fit(dataset)
        with(cls.root) {
            assertIs<DecisionTreeModel.InternalNode>(this)
            assertEquals(f1, split.feature)
            with(split) {
                assertIs<DecisionTreeModel.CategoricalSplit<String>>(this)
                assertEquals((0..10).mapToSet(Int::toString), right)
                assertEquals((11..17).mapToSet(Int::toString), left)
            }
            with(left) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertFalse { decision }
            }
            with(right) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertTrue { decision }
            }
        }
    }

    @Test
    fun `two features string relevant long irrelevant and missing`() {
        val f1 = Feature("f1", String::class)
        val f2 = Feature("f2", Long::class)
        val dataset = PropositionalSparseDataset((0..17).map {
            if (it <= 5)
                mapOf(f1 to it.toString(), f2 to 10L, Label to (it <= 10))
            else
                mapOf(f1 to it.toString(), Label to (it <= 10))
        })
        val cls = SparkDecisionTreeClassifier().fit(dataset)
        with(cls.root) {
            assertIs<DecisionTreeModel.InternalNode>(this)
            assertEquals(f1, split.feature)
            with(split) {
                assertIs<DecisionTreeModel.CategoricalSplit<String>>(this)
                assertEquals((0..10).mapToSet(Int::toString), right)
                assertEquals((11..17).mapToSet(Int::toString), left)
            }
            with(left) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertFalse { decision }
            }
            with(right) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertTrue { decision }
            }
        }
    }

    @Test
    fun golf() {
        val text = """Rainy,Hot,High,FALSE,No
Rainy,Hot,High,TRUE,No
Overcast,Hot,High,FALSE,Yes
Sunny,Mild,High,FALSE,Yes
Sunny,Cool,Normal,FALSE,Yes
Sunny,Cool,Normal,TRUE,No
Overcast,Cool,Normal,TRUE,Yes
Rainy,Mild,High,FALSE,No
Rainy,Cool,Normal,FALSE,Yes
Sunny,Mild,Normal,FALSE,Yes
Rainy,Mild,Normal,TRUE,Yes
Overcast,Mild,High,TRUE,Yes
Overcast,Hot,Normal,FALSE,Yes
Sunny,Mild,High,TRUE,No"""
        val outlook = Feature("outlook", String::class)
        val temp = Feature("temperature", String::class)
        val humidity = Feature("humidity", String::class)
        val windy = Feature("windy", Boolean::class)
        val dataset = PropositionalSparseDataset(text.split("\n").map { line ->
            val fields = line.split(",")
            mapOf(
                outlook to fields[0],
                temp to fields[1],
                humidity to fields[2],
                windy to fields[3].equals("true", ignoreCase = true),
                Label to fields[4].equals("yes", ignoreCase = true)
            )
        })
        val cls = SparkDecisionTreeClassifier().fit(dataset)
        with(cls.root) {
            assertIs<DecisionTreeModel.InternalNode>(this)
            assertEquals(outlook, split.feature)
            assertIs<DecisionTreeModel.CategoricalSplit<String>>(split)
            assertEquals(setOf("Rainy", "Sunny"), (split as DecisionTreeModel.CategoricalSplit<String>).left)
            assertEquals(setOf("Overcast"), (split as DecisionTreeModel.CategoricalSplit<String>).right)
            with(right) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertTrue { decision }
            }
            with(left) {
                println(this)
                assertIs<DecisionTreeModel.InternalNode>(this)
                assertEquals(humidity, split.feature)
                assertIs<DecisionTreeModel.CategoricalSplit<String>>(split)
                assertEquals(setOf("High"), (split as DecisionTreeModel.CategoricalSplit<String>).left)
                assertEquals(setOf("Normal"), (split as DecisionTreeModel.CategoricalSplit<String>).right)
                with(left) {
                    assertIs<DecisionTreeModel.InternalNode>(this)
                    assertEquals(outlook, split.feature)
                    assertIs<DecisionTreeModel.CategoricalSplit<String>>(split)
                    assertEquals(setOf("Rainy"), (split as DecisionTreeModel.CategoricalSplit<String>).left)
                    assertEquals(
                        setOf("Overcast", "Sunny"),
                        (split as DecisionTreeModel.CategoricalSplit<String>).right
                    )
                }
                with(right) {
                    assertIs<DecisionTreeModel.InternalNode>(this)
                    assertEquals(windy, split.feature)
                    assertIs<DecisionTreeModel.CategoricalSplit<String>>(split)
                }
            }
        }
    }

    @Test
    fun `two integer features one missing`() {
        val f1 = Feature("f1", Int::class)
        val f2 = Feature("f2", Int::class)
        val dataset = PropositionalSparseDataset((0..17).map {
            if (it <= 10)
                mapOf(f1 to 10, f2 to it, Label to (it <= 10))
            else
                mapOf(f1 to 10, Label to (it <= 10))
        })
        val cls = SparkDecisionTreeClassifier().fit(dataset)
        println(cls.toMultilineString())
        with(cls.root) {
            assertIs<DecisionTreeModel.InternalNode>(this)
            assertEquals(f2, split.feature)
            with(split) {
                assertIs<DecisionTreeModel.ContinuousSplit<Double>>(this)
                assertDoubleEquals(4.5, threshold)
            }
            with(left) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertTrue { decision }
            }
            with(right) {
                assertIs<DecisionTreeModel.InternalNode>(this)
                assertEquals(f2, split.feature)
                with(split) {
                    assertIs<DecisionTreeModel.ContinuousSplit<Double>>(this)
                    assertDoubleEquals(5.5, threshold)
                }
                with(left) {
                    assertIs<DecisionTreeModel.Leaf>(this)
                    assertFalse { decision }
                }
                with(right) {
                    assertIs<DecisionTreeModel.Leaf>(this)
                    assertTrue { decision }
                }
            }
        }
    }

    @Test
    fun `two long features one missing`() {
        val f1 = Feature("f1", Long::class)
        val f2 = Feature("f2", Long::class)
        val dataset = PropositionalSparseDataset((0L..17L).map {
            if (it <= 10)
                mapOf(f1 to 10L, f2 to it, Label to (it <= 10L))
            else
                mapOf(f1 to 10L, Label to (it <= 10L))
        })
        val cls = SparkDecisionTreeClassifier().fit(dataset)
        println(cls.toMultilineString())
        with(cls.root) {
            assertIs<DecisionTreeModel.InternalNode>(this)
            assertEquals(f2, split.feature)
            with(split) {
                assertIs<DecisionTreeModel.ContinuousSplit<Double>>(this)
                assertDoubleEquals(4.5, threshold)
            }
            with(left) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertTrue { decision }
            }
            with(right) {
                assertIs<DecisionTreeModel.InternalNode>(this)
                assertEquals(f2, split.feature)
                with(split) {
                    assertIs<DecisionTreeModel.ContinuousSplit<Double>>(this)
                    assertDoubleEquals(5.5, threshold)
                }
                with(left) {
                    assertIs<DecisionTreeModel.Leaf>(this)
                    assertFalse { decision }
                }
                with(right) {
                    assertIs<DecisionTreeModel.Leaf>(this)
                    assertTrue { decision }
                }
            }
        }
    }

    @Test
    fun `two string features one missing`() {
        val f1 = Feature("f1", String::class)
        val f2 = Feature("f2", String::class)
        val dataset = PropositionalSparseDataset((0..17).map {
            if (it <= 10)
                mapOf(f1 to "10", f2 to it.toString(), Label to (it <= 10))
            else
                mapOf(f1 to "10", Label to (it <= 10))
        })
        val cls = SparkDecisionTreeClassifier().fit(dataset)
        with(cls.root) {
            assertIs<DecisionTreeModel.InternalNode>(this)
            assertEquals(f2, split.feature)
            with(split) {
                assertIs<DecisionTreeModel.CategoricalSplit<String>>(this)
                assertEquals(setOf<String?>(null), left)
                assertEquals((0..10).mapToSet(Int::toString), right)
            }
            with(left) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertFalse { decision }
            }
            with(right) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertTrue { decision }
            }
        }
    }

    @Test
    fun `two boolean features one missing`() {
        val f1 = Feature("f1", Boolean::class)
        val f2 = Feature("f2", Boolean::class)
        val dataset = PropositionalSparseDataset((0..17).map {
            if (it <= 10)
                mapOf(f1 to true, f2 to (it <= 10), Label to (it <= 10))
            else
                mapOf(f1 to true, Label to (it <= 10))
        })
        val cls = SparkDecisionTreeClassifier().fit(dataset)
        with(cls.root) {
            assertIs<DecisionTreeModel.InternalNode>(this)
            assertEquals(f2, split.feature)
            with(split) {
                assertIs<DecisionTreeModel.CategoricalSplit<Boolean>>(this)
                assertEquals(setOf(false), left)
                assertEquals(setOf(true), right)
            }
            with(left) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertFalse { decision }
            }
            with(right) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertTrue { decision }
            }
        }
    }

    @Test
    fun `two instant features`() {
        val f1 = Feature("f1", Instant::class)
        val f2 = Feature("f2", Instant::class)
        val dataset = PropositionalSparseDataset((0..17).map {
            mapOf(
                f1 to Instant.parse("2022-05-16T${it.toString().padStart(2, '0')}:00:00.00Z"),
                f2 to Instant.ofEpochMilli(0L),
                Label to (it >= 9)
            )
        })
        val cls = SparkDecisionTreeClassifier().fit(dataset)
        with(cls.root) {
            assertIs<DecisionTreeModel.InternalNode>(this)
            assertEquals(f1, split.feature)
            with(split) {
                assertIs<DecisionTreeModel.ContinuousSplit<Instant>>(this)
                assertTrue { threshold > Instant.parse("2022-05-16T08:00:00.00Z") }
                assertTrue { threshold < Instant.parse("2022-05-16T09:00:00.00Z") }
            }
            with(left) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertFalse { decision }
            }
            with(right) {
                assertIs<DecisionTreeModel.Leaf>(this)
                assertTrue { decision }
            }
        }
    }
}