package processm.conformance

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet

/**
 * Causal nets for tests.
 */
object CausalNets {

    /**
     * A Causal net based on Fig. 3.12 from Process Mining: Data Science in Action book.
     */
    val fig312: CausalNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val f = Node("f")
        val g = Node("g")
        val h = Node("h")
        val z = Node("z")
        causalnet {
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
    }

    /**
     * A Causal net based on Fig. 3.16 from Process Mining: Data Science in Action book.
     */
    val fig316: CausalNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        causalnet {
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
    }

    /**
     * A Causal net implementing a flower model of activities A-Z.
     */
    val azFlower: CausalNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val activities = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".map { Node(it.toString()) }
        val tau = Node("τ", isSilent = true)
        causalnet {
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
    }

    /**
     * A Causal net made of two flower models, the first containing activities A-M, the second activities N-Z.
     */
    val parallelFlowers: CausalNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val activities1 = "ABCDEFGHIJKLM".map { Node(it.toString()) }
        val activities2 = "NOPQRSTUVWXYZ".map { Node(it.toString()) }

        val st = Node("start", isArtificial = true)
        val en = Node("end", isArtificial = true)

        val loopStart = Node("ls")
        val loopEnd = Node("le")

        val dec1 = Node("d1")
        val dec2 = Node("d2")

        causalnet {
            start = st
            end = en

            st splits loopStart
            st joins loopStart
            loopStart splits dec1 + dec2

            loopStart joins dec1
            for (act1 in activities1) {
                dec1 splits act1
                dec1 joins act1
                act1 splits loopEnd
                for (act2 in activities2) {
                    act1 + act2 join loopEnd
                }
            }

            loopStart joins dec2
            for (act2 in activities2) {
                dec2 splits act2
                dec2 joins act2
                act2 splits loopEnd
            }

            loopEnd splits loopStart
            loopEnd joins loopStart

            loopEnd splits en
            loopEnd joins en
        }
    }
}
