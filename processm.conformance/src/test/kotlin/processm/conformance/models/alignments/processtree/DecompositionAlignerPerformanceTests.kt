package processm.conformance.models.alignments.processtree

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.CompositeAligner
import processm.core.log.Helpers
import processm.core.log.hierarchical.Log
import processm.core.models.processtree.ProcessTree
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse

@Disabled("These tests are intended for manual execution")
class DecompositionAlignerPerformanceTests {

    companion object {
        const val REPETITIONS = 100

        val pool = Executors.newCachedThreadPool()
        val totals = LinkedHashMap<String, Long>()

        @JvmStatic
        @BeforeAll
        fun configure() {
            assertFalse(this::class.java.desiredAssertionStatus(), "Disable assertions (-da) for reliable results.")
        }

        @JvmStatic
        @AfterAll
        fun printSummary() {
            pool.shutdownNow()
            println("Totals:")
            for ((algorithm, time) in totals) {
                println(
                    String.format(
                        "%-20s: %5dms%s",
                        algorithm,
                        time,
                        if (time == totals.values.minOrNull()) "*" else ""
                    )
                )
            }
            pool.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    fun compare(tree: ProcessTree, log: Log) {
        val algorithms = listOf(
            AStar(tree),
            DecompositionAligner(tree),
            CompositeAligner(tree, pool = pool)
        )

        // warm up
        for (trace in log.traces) {
            for (algorithm in algorithms) {
                algorithm.align(trace)
            }
        }

        System.gc()

        // actual test
        val times = algorithms.map { algorithm ->
            val start = System.currentTimeMillis()
            repeat(REPETITIONS) {
                for (trace in log.traces) {
                    val alignment = algorithm.align(trace)
                }
            }
            System.currentTimeMillis() - start
        }

        for ((algorithm, time) in algorithms zip times) {
            println(
                String.format(
                    "%-20s: %5dms%s",
                    algorithm::class.simpleName,
                    time,
                    if (time == times.minOrNull()) "*" else ""
                )
            )
            totals.compute(algorithm::class.simpleName!!) { _, old ->
                (old ?: 0L) + time
            }
        }
    }

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

        compare(tree, log)
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

        compare(tree, log)
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

        compare(tree, log)
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

        compare(tree, log)
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

        compare(tree, log)
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

        compare(tree, log)
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

        compare(tree, log)
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

        compare(tree, log)
    }

    @Test
    fun `PM book Fig 7 29 non-conforming log`() {
        val tree = ProcessTree.parse("→(×(→(A,∧(C,E)),→(B,∧(D,F))),G)")
        val log = Helpers.logFromString(
            """
                D F B G E C A
                """
        )

        compare(tree, log)
    }
}
