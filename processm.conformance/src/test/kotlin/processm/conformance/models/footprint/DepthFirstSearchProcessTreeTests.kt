package processm.conformance.models.footprint

import org.junit.jupiter.api.Test
import processm.core.log.Helpers
import processm.core.log.hierarchical.toFlatSequence
import processm.core.models.processtree.ProcessTree
import kotlin.test.assertEquals

class DepthFirstSearchProcessTreeTests {
    @Test
    fun `PM book Fig 7 27 conforming log`() {
        val tree = ProcessTree.parse("→(A,⟲(→(∧(×(B,C),D),E),F),×(G,H))")
        val log = Helpers.logFromString(
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

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(tree)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0, model.precision)
    }

    @Test
    fun `PM book Fig 7 27 non-conforming log`() {
        val tree = ProcessTree.parse("→(A,⟲(→(∧(×(B,C),D),E),F),×(G,H))")
        val log = Helpers.logFromString(
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

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(tree)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 24.0 / 81.0, model.fitness)
        assertEquals(1.0, model.precision)
    }

    @Test
    fun `Flower process tree`() {
        val tree = ProcessTree.parse("⟲(τ,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z)")
        val log = Helpers.logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A A A A A A A A A A A A A A A A A A A A A A A A A A
                Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z
                A
                Z
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(tree)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 624.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `Parallel flower models`() {
        val tree = ProcessTree.parse("∧(⟲(τ,A,C,E,G,I,K,M,O,Q,S,U,W,Y),⟲(τ,B,D,F,H,J,L,N,P,R,T,V,X,Z))")
        val log = Helpers.logFromString(
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

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(tree)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 624.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `Parallel flower models non-conforming log`() {
        val tree = ProcessTree.parse("∧(⟲(τ,A,C,E,G,I,K,M,O,Q,S,U,W,Y),⟲(τ,B,D,F,H,J,L,N,P,R,T,V,X,Z))")
        val log = Helpers.logFromString(
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

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(tree)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 18.0 / (33.0 * 33.0), model.fitness)
        assertEquals(1.0 - 626.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `Parallel decisions in loop process tree`() {
        val tree = ProcessTree.parse("⟲(∧(×(A,C,E,G,I,K,M,O,Q,S,U,W,Y),×(B,D,F,H,J,L,N,P,R,T,V,X,Z)),τ)")
        val log = Helpers.logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A Z Z A A Z Z A A Z
                A Z
                Z A
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(tree)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 622.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `Parallel decisions in loop non-conforming log`() {
        val tree = ProcessTree.parse("⟲(∧(×(A,C,E,G,I,K,M,O,Q,S,U,W,Y),×(B,D,F,H,J,L,N,P,R,T,V,X,Z)),τ)")
        val log = Helpers.logFromString(
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

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(tree)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 622.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `PM book Fig 7 29 conforming log`() {
        val tree = ProcessTree.parse("→(×(→(A,∧(C,E)),→(B,∧(D,F))),G)")
        val log = Helpers.logFromString(
            """
                A C E G
                A E C G
                B D F G
                B F D G
                """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(tree)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0, model.precision)
    }

    @Test
    fun `PM book Fig 7 29 non-conforming log`() {
        val tree = ProcessTree.parse("→(×(→(A,∧(C,E)),→(B,∧(D,F))),G)")
        val log = Helpers.logFromString(
            """
                D F B G E C A
                """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(tree)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 8.0 / 49.0, model.fitness)
        assertEquals(1.0 - 20.0 / 49.0, model.precision)
    }
}
