package processm.conformance.models.footprint

import processm.conformance.PetriNets.azFlower
import processm.conformance.PetriNets.fig314
import processm.conformance.PetriNets.fig32
import processm.conformance.PetriNets.fig34c
import processm.conformance.PetriNets.fig73
import processm.conformance.PetriNets.parallelFlowers
import processm.core.helpers.allPermutations
import processm.core.log.Helpers
import processm.core.log.Helpers.logFromString
import processm.core.log.hierarchical.Trace
import processm.core.log.hierarchical.toFlatSequence
import kotlin.test.Test
import kotlin.test.assertEquals

class DepthFirstSearchPetriNetTests {

    @Test
    fun `PM book Fig 3 2 conforming log`() {
        val log = logFromString(
            """
                a b d e g
                a b d e h
                a b d e f c d e h
                a c d e g
                a c d e h
                a c d e f b d e g
                a d c e f d c e f b d e h
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(fig32)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 4.0 / 64.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 2 non-conforming log`() {
        val log = logFromString(
            """
                a c e f b e f d b e g
                a b c d e h
                b d e f c d e h
                a c d f b d e g
                a c d e f e h
                a g
                a b c d e f d c b e f g h
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(fig32)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 12.0 / 64.0, model.fitness)
        assertEquals(1.0 - 2.0 / 64.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 4 c conforming log`() {
        val t1 = fig34c.transitions.first { it.name == "t1" }
        val t2 = fig34c.transitions.first { it.name == "t2" }
        val t3 = fig34c.transitions.first { it.name == "t3" }
        val t4 = fig34c.transitions.first { it.name == "t4" }
        val t5 = fig34c.transitions.first { it.name == "t5" }
        val allMoves = List(5) { t1 } + List(5) { t2 } + List(5) { t3 } + List(5) { t4 } + List(5) { t5 }
        val limit = 10000
        val log = allMoves
            .allPermutations()
            .take(limit)
            .map { activities -> Trace(activities.asSequence().map { Helpers.event(it.name) }) }


        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(fig34c)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 18.0 / 25.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 4 c non-conforming log`() {
        val t2 = fig34c.transitions.first { it.name == "t2" }
        val t3 = fig34c.transitions.first { it.name == "t3" }
        val t4 = fig34c.transitions.first { it.name == "t4" }
        val t5 = fig34c.transitions.first { it.name == "t5" }
        // missing t1s
        val allMoves = List(5) { t2 } + List(5) { t3 } + List(5) { t4 } + List(5) { t5 }
        val limit = 10000
        val log = allMoves
            .allPermutations()
            .take(limit)
            .map { activities -> Trace(activities.asSequence().map { Helpers.event(it.name) }) }


        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(fig34c)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 19.0 / 25.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 14 conforming log`() {
        val log = logFromString(
            """
                a b e
                a c e
                a b d e
                a d b e
                a c d e
                a d c e
                a b c d e
                a b d c e
                a d b c e
                a c b d e
                a c d b e
                a d c b e
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(fig314)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 7.0 / 25.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 14 non-conforming log`() {
        val log = logFromString(
            """
                a b e z
                a c 
                a d e
                a b b c e
                a e
                d c b e
                x y z
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(fig314)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 9.0 / 64.0, model.fitness)
        assertEquals(1.0 - 11.0 / 25.0, model.precision)
    }

    @Test
    fun `PM book Fig 7 3 conforming log`() {
        val log = logFromString(
            """
                a b c e
                a c b e
                d a d b d c d e d
                d d d d d d a d d d d d d d c d d d d d d d b d d d d d d d e d d d d d d d
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(fig73)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        // the execution of d at the end of the trace requires the model to pass behind the final marking
        assertEquals(1.0, model.fitness)
        assertEquals(1.0, model.precision)
    }

    @Test
    fun `PM book Fig 7 3 non-conforming log`() {
        val log = logFromString(
            """
                a b e
                a c e
                d a z d b d d e d
                d d d d d d a d d d d d d d c d d d d d d d b d d d d d d d d d d d d d d
                d a e d d b c e
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(fig73)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        // the execution of d at the end of the trace requires the model to pass behind the final marking
        assertEquals(1.0 - 6.0 / 36.0, model.fitness)
        assertEquals(1.0 - 2.0 / 25.0, model.precision)
    }

    @Test
    fun `Flower model conforming log`() {
        val log = logFromString(
            """
                a b c d e f g h i j k l m n o p q r s t u w v x y z
                z y x v w u t s r q p o n m l k j i h g f e d c b a
                a a a a a a a a a a a a a z z z z z z z z z z z z z
                z z z z z z z z z z z z z a a a a a a a a a a a a a
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(azFlower)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 622.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `Flower model non-conforming log`() {
        val log = logFromString(
            """
                1 a b c 5 d e f 09 g h i 13 j k l m n o p q r s t u w v x y z
                z 2 y x v 6 w u t 10 s r q 14 p o n m l k j i h g f e d c b a
                a a 3 a a a 7 a a a 11 a a a 15 a a z z z z z z z z z z z z z
                z z z 4 z z z 8 z z z 12 z z z 16 z a a a a a a a a a a a a a
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(azFlower)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 46.0 / (42.0 * 42.0), model.fitness)
        assertEquals(1.0 - 636.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `Parallel flower models in loop conforming log`() {
        val log = logFromString(
            """
                a b c d e f g h i j k l m n o p q r s t u w v x y z
                z y x v w u t s r q p o n m l k j i h g f e d c b a
                a a a a a a a a a a a a a z z z z z z z z z z z z z
                z z z z z z z z z z z z z a a a a a a a a a a a a a
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(parallelFlowers)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 622.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `Parallel flower models in loop non-conforming log`() {
        val log = logFromString(
            """
                a a a a a a a a a a a a a z 1
                a b c 1 d e f g h i j k l m
                z y x v w u t s r q 1 p o n 2
                2 z z z z z z z z 1 z z z z a
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(parallelFlowers)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 14.0 / (28.0 * 28.0), model.fitness)
        assertEquals(1.0 - 672.0 / (26.0 * 26.0), model.precision)
    }
}
