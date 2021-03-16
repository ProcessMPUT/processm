package processm.conformance.models.alignments

import processm.core.log.Helpers.logFromString
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.processtree.ProcessTree
import kotlin.test.Test
import kotlin.test.assertEquals

class AStarTests {

    @Test
    fun `PM book Fig 7 27 conforming log`() {
        val tree = ProcessTree.parse("→(A,⟲(→(∧(×(B,C),D),E),F),×(G,H))")
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

        val astar = AStar(tree)
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
    fun `PM book Fig 7 27 non-conforming log`() {
        val tree = ProcessTree.parse("→(A,⟲(→(∧(×(B,C),D),E),F),×(G,H))")
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

        val expectedCosts = listOf(
            2,
            2,
            1,
            1,
            1,
            1,
            1,
            1,
            6,
            2,
            4
        )

        val astar = AStar(tree)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.count { it.logMove !== null })
        }
    }

    @Test
    fun `Flower process tree`() {
        val tree = ProcessTree.parse("⟲(τ,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z)")
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

        val astar = AStar(tree)
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
        val tree = ProcessTree.parse("∧(⟲(τ,A,C,E,G,I,K,M,O,Q,S,U,W,Y),⟲(τ,B,D,F,H,J,L,N,P,R,T,V,X,Z))")
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

        val astar = AStar(tree)
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
        val tree = ProcessTree.parse("∧(⟲(τ,A,C,E,G,I,K,M,O,Q,S,U,W,Y),⟲(τ,B,D,F,H,J,L,N,P,R,T,V,X,Z))")
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

        val astar = AStar(tree)
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
    fun `Parallel decisions in loop`() {
        val tree = ProcessTree.parse("⟲(∧(×(A,C,E,G,I,K,M,O,Q,S,U,W,Y),×(B,D,F,H,J,L,N,P,R,T,V,X,Z)),τ)")
        val log = logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A Z Z A A Z Z A A Z
                A Z
                Z A
            """
        )

        val astar = AStar(tree)
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
        val tree = ProcessTree.parse("⟲(∧(×(A,C,E,G,I,K,M,O,Q,S,U,W,Y),×(B,D,F,H,J,L,N,P,R,T,V,X,Z)),τ)")
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

        val astar = AStar(tree)
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
        val tree = ProcessTree.parse("→(×(→(A,∧(C,E)),→(B,∧(D,F))),G)")
        val log = logFromString(
            """
                A C E G
                A E C G
                B D F G
                B F D G
                """
        )

        val astar = AStar(tree)
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
        val tree = ProcessTree.parse("→(×(→(A,∧(C,E)),→(B,∧(D,F))),G)")
        val log = logFromString(
            """
                D F B G E C A
                """
        )

        val expectedCosts = listOf(
            5,
        )

        val astar = AStar(tree)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
        }
    }

    @Test
    fun `PM book Fig 3 12 conforming log`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val f = Node("f")
        val g = Node("g")
        val h = Node("h")
        val z = Node("z")
        val model = causalnet {
            start = a
            end = z
            a splits b + d
            a splits c + d

            a joins b
            f joins b
            b splits e

            a joins c
            f joins c
            c splits e

            a joins d
            f joins d
            d splits e

            b + d join e
            c + d join e
            e splits g
            e splits h
            e splits f

            e joins f
            f splits b + d
            f splits c + d

            e joins g
            g splits z

            e joins h
            h splits z

            g joins z
            h joins z
        }

        val log = logFromString(
            """
                a b d e g z
                a d b e g z
                a c d e g z
                a d c e g z
                a d c e f b d e g z
                a d c e f b d e h z
                """
        )

        val astar = AStar(model)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
        }
    }

    @Test
    fun `PM book Fig 3 12 non-conforming log`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val f = Node("f")
        val g = Node("g")
        val h = Node("h")
        val z = Node("z")
        val model = causalnet {
            start = a
            end = z
            a splits b + d
            a splits c + d

            a joins b
            f joins b
            b splits e

            a joins c
            f joins c
            c splits e

            a joins d
            f joins d
            d splits e

            b + d join e
            c + d join e
            e splits g
            e splits h
            e splits f

            e joins f
            f splits b + d
            f splits c + d

            e joins g
            g splits z

            e joins h
            h splits z

            g joins z
            h joins z
        }

        val log = logFromString(
            """
                a b c d e g z
                a d b e g
                a c d e g z x
                a d c e g x z
                a d c e b d e g z
                a d c e b d e h z
                """
        )

        val expectedCost = arrayOf(
            1,
            1,
            1,
            1,
            1,
            1
        )

        val astar = AStar(model)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCost[i], alignment.cost)
        }
    }

    @Test
    fun `PM book Fig 3 16 conforming log`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val model = causalnet {
            start = a
            end = e
            a splits b

            a joins b
            b joins b
            b splits c + d
            b splits b + c

            b joins c
            c splits d

            b + c join d
            c + d join d
            d splits d
            d splits e

            d joins e
        }

        val log = logFromString(
            """
                a b c d e
                a b c b d c d e
                a b b c c d d e
                a b c b c b c b c d d d d e
                """
        )

        val astar = AStar(model)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
        }
    }

    @Test
    fun `PM book Fig 3 16 non-conforming log`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val model = causalnet {
            start = a
            end = e
            a splits b

            a joins b
            b joins b
            b splits c + d
            b splits b + c

            b joins c
            c splits d

            b + c join d
            c + d join d
            d splits d
            d splits e

            d joins e
        }

        val log = logFromString(
            """
                a b d e
                a b c b d d e
                a b b c d d e
                a b c b c b d b c d d d e
                """
        )

        val expectedCost = arrayOf(
            1,
            1,
            1,
            2
        )

        val astar = AStar(model)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCost[i], alignment.cost)
        }
    }

    @Test
    fun `Flower C-net`() {
        val activities = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".map { Node(it.toString()) }
        val tau = Node("τ", isSilent = true)
        val model = causalnet {
            start = activities.first()
            end = activities.last()

            start splits tau
            start joins tau

            tau splits end
            tau joins end

            for (activity in activities.subList(1, activities.size - 1)) {
                tau splits activity
                tau joins activity
                activity splits tau
                activity joins tau
            }
        }

        val log = logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                A Y X W V U T S R Q P O N M L K J I H G F E D C B Z
                A B B B B B B B B B B B B B B B B B B B B B B B B Z
                A Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Z
                A Z
            """
        )

        val astar = AStar(model)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count() * 2 - 1, alignment.steps.size)
        }
    }
}
