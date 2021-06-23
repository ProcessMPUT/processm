package processm.conformance.models.alignments

import processm.conformance.models.alignments.cache.CachingAlignerFactory
import processm.conformance.models.alignments.cache.DefaultAlignmentCache
import processm.conformance.models.alignments.petrinet.DecompositionAligner
import processm.core.helpers.SameThreadExecutorService
import processm.core.log.Helpers
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.petrinet.converters.toPetriNet
import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeAlignerCacheTests {


    @Test
    fun `same model twice`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val f = Node("f")
        val cnet = causalnet {
            start = a
            end = f
            a splits b
            b splits c + d
            c splits e
            d splits e
            e splits b or f
            a or e join b
            b joins c
            b joins d
            c + d join e
            e joins f
        }


        val log = Helpers.logFromString(
            """
                a b c d e f
                a b c d e b c d e f
        """.trimIndent()
        )
        val cache = DefaultAlignmentCache()
        for (trace in log.traces) {
            val aligner = CompositeAligner(
                cnet,
                PenaltyFunction(),
                SameThreadExecutorService,
                null,
                CachingAlignerFactory(cache) { model, penalty, _ -> AStar(model, penalty) })
            aligner.align(trace)
        }
        assertEquals(0, cache.hitCounter)
        for (trace in log.traces) {
            val aligner = CompositeAligner(
                cnet,
                PenaltyFunction(),
                SameThreadExecutorService,
                null,
                CachingAlignerFactory(cache) { model, penalty, _ -> AStar(model, penalty) })
            aligner.align(trace)
        }
        assertEquals(2, cache.hitCounter)
    }

    @Test
    fun `cnet1 is a submodel of cnet2`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val f = Node("f")
        val cnet1 = causalnet {
            start = a
            end = f
            a splits b + c
            b splits d
            c splits d
            d splits f
            a joins b
            a joins c
            b + c join d
            d joins f
        }

        val cnet2 = causalnet {
            start = a
            end = f
            a splits b + c or e
            b splits d
            c splits d
            d splits f
            e splits f
            a joins b
            a joins c
            a joins e
            b + c join d
            d or e join f
        }


        val log = Helpers.logFromString(
            """
                a b c d f                
        """.trimIndent()
        )
        val cache = DefaultAlignmentCache()
        for (trace in log.traces) {
            val aligner = CompositeAligner(
                cnet1,
                PenaltyFunction(),
                SameThreadExecutorService,
                null,
                CachingAlignerFactory(cache) { model, penalty, _ ->
                    DecompositionAligner(
                        (model as CausalNet).toPetriNet(),
                        penalty,
                        alignerFactory = CachingAlignerFactory(cache) { m, p, _ -> AStar(m, p) })
                }
            )
            aligner.align(trace)
        }
        assertEquals(0, cache.hitCounter)
        for (trace in log.traces) {
            val aligner = CompositeAligner(
                cnet2,
                PenaltyFunction(),
                SameThreadExecutorService,
                null,
                CachingAlignerFactory(cache) { model, penalty, _ ->
                    DecompositionAligner(
                        (model as CausalNet).toPetriNet(),
                        penalty,
                        alignerFactory = CachingAlignerFactory(cache) { m, p, _ -> AStar(m, p) })
                }
            )
            aligner.align(trace)
        }
        assertEquals(3, cache.hitCounter)
    }
}