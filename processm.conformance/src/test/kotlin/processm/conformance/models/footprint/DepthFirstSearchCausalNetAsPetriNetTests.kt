package processm.conformance.models.footprint

import processm.core.helpers.allSubsets
import processm.core.log.Helpers
import processm.core.log.hierarchical.toFlatSequence
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.petrinet.converters.toPetriNet
import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DepthFirstSearchCausalNetAsPetriNetTests {
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
        val net = causalnet {
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

        val log = Helpers.logFromString(
            """
                a b d e g z
                a d b e g z
                a c d e g z
                a d c e g z
                a d c e f b d e g z
                a d c e f b d e h z
                """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net.toPetriNet())
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 4.0 / 81.0, model.precision)
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
        val net = causalnet {
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

        val log = Helpers.logFromString(
            """
                a b c d e g z
                a d b e g
                a c d e g z x
                a d c e g x z
                a d c e b d e g z
                a d c e b d e h z
                """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net.toPetriNet())
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 8.0 / 81.0, model.fitness)
        assertEquals(1.0 - 8.0 / 81.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 16 conforming log`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val net = causalnet {
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

        val log = Helpers.logFromString(
            """
                a b c d e
                a b c b d c d e
                a b b c c d d e
                a b c b c b c b c d d d d e
                """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net.toPetriNet())
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 2.0 / 25.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 16 non-conforming log`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val net = causalnet {
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

        val log = Helpers.logFromString(
            """
                a b d e
                a b c b d d e
                a b b c d d e
                a b c b c b d b c d d d e
                """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net.toPetriNet())
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 2.0 / 25.0, model.fitness)
        assertEquals(1.0 - 5.0 / 25.0, model.precision)
    }

    @Test
    fun `Flower C-net`() {
        val activities = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".map { Node(it.toString()) }
        val tau = Node("τ", isSilent = true)
        val net = causalnet {
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

        val log = Helpers.logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                A Y X W V U T S R Q P O N M L K J I H G F E D C B Z
                A B B B B B B B B B B B B B B B B B B B B B B B B Z
                A Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Z
                A Z
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net.toPetriNet())
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 616.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `Parallel decisions in loop C-net conforming log`() {
        val activities1 = "ABCDEFGHIJKLM".map { Node(it.toString()) }
        val activities2 = "NOPQRSTUVWXYZ".map { Node(it.toString()) }

        val st = Node("start", special = true)
        val en = Node("end", special = true)

        val loopStart = Node("ls")
        val loopEnd = Node("le")

        val dec1 = Node("d1")
        val dec2 = Node("d2")

        val net = causalnet {
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

        val log = Helpers.logFromString(
            """
                ls d1 M d2 Z le
                ls d1 d2 A N le ls d1 C d2 O le ls d1 D d2 P le ls d2 d1 E Q le ls d1 d2 F R le ls d2 d1 G S le ls d1 H d2 T le ls d1 I d2 U le ls d2 d1 J V le ls d1 d2 K W le ls d1 L d2 X le ls d1 M d2 Y le
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net.toPetriNet())
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 436.0 / (30.0 * 30.0), model.precision)
    }

    @Test
    fun `Parallel decisions in loop C-net non-conforming log`() {
        val activities1 = "ABCDEFGHIJKLM".map { Node(it.toString()) }
        val activities2 = "NOPQRSTUVWXYZ".map { Node(it.toString()) }

        val st = Node("start", special = true)
        val en = Node("end", special = true)

        val loopStart = Node("ls")
        val loopEnd = Node("le")

        val dec1 = Node("d1")
        val dec2 = Node("d2")

        val net = causalnet {
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

        val log = Helpers.logFromString(
            """
                ls d2 M d1 Z le
                d2 ls d1 Z M le
                ls d1 d2 A N ls le ls d1 C d2 O le ls d1 D d2 P le ls d2 d1 E Q le ls d1 d2 F R le ls d2 d1 G S le ls d1 H d2 T le ls d1 I d2 U le ls d2 d1 J V le ls d1 d2 K W le ls d1 L d2 X ls le d1 M d2 Y le
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net.toPetriNet())
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 12.0 / (29.0 * 29.0), model.fitness)
        assertEquals(1.0 - 438.0 / (30.0 * 30.0), model.precision)
    }

    @Test
    fun `Parallel decisions in loop with many splits C-net non-conforming log`() {
        val activities1 = "ABCDEFGHIJKLM".map { Node(it.toString()) }
        val activities2 = "NOPQRSTUVWXYZ".map { Node(it.toString()) }

        val st = Node("start", special = true)
        val en = Node("end", special = true)

        val loopStart = Node("ls")
        val loopEnd = Node("le")

        val dec1 = Node("d1")
        val dec2 = Node("d2")

        val net = causalnet {
            start = st
            end = en

            st splits loopStart
            st joins loopStart
            loopStart splits dec1 + dec2

            loopStart joins dec1
            for (act1 in activities1) {
                dec1 joins act1
                act1 splits loopEnd
                for (act2 in activities2) {
                    act1 + act2 join loopEnd
                }
            }

            for (act1 in activities1.allSubsets(true).filter { it.size <= 3 }) {
                dec1 splits act1
            }

            loopStart joins dec2
            for (act2 in activities2) {
                dec2 splits act2
                dec2 joins act2
                act2 splits loopEnd
            }

            for (act2 in activities1.allSubsets(true).filter { it.size <= 3 }) {
                dec2 splits act2
            }

            loopEnd splits loopStart
            loopEnd joins loopStart

            loopEnd splits en
            loopEnd joins en
        }

        val log = Helpers.logFromString(
            """
                ls d2 M d1 Z le
                d2 ls d1 Z M le
                ls d1 d2 A N ls le ls d1 C d2 O le
                ls d1 d2 A N ls le ls d1 C d2 O le ls d1 D d2 P le ls d2 d1 E Q le ls d1 d2 F R le ls d2 d1 G S le ls d1 H d2 T le ls d1 I d2 U le ls d2 d1 J V le ls d1 d2 K W le ls d1 L d2 X ls le d1 M d2 Y le
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net, 4, 200)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 8.0 / (29.0 * 29.0), model.fitness)
        // this result is unstable because CausalNet.available() yields activities in an unstable order
        assertTrue((1.0 - 653.0 / (30.0 * 30.0) - model.precision).absoluteValue < 0.01)
    }
}
