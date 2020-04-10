package processm.core.comparators

import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import kotlin.test.*

class CausalNetTraceComparisonTest {

    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")

    val model1a = causalnet {
        start = a
        end = d
        a splits b
        b splits c
        c splits d
        a joins b
        b joins c
        c joins d
    }

    val model1b = causalnet {
        start = a
        end = d
        a splits c
        c splits b
        b splits d
        a joins c
        c joins b
        b joins d
    }

    val model1c = causalnet {
        start splits a
        start joins a
        a splits b
        b splits c
        c splits d
        a joins b
        b joins c
        c joins d
        d splits end
        d joins end
    }

    val model2 = causalnet {
        start = a
        end = d
        a splits b + c
        b splits d
        c splits d
        a joins b
        a joins c
        b + c join d
    }

    val model3 = causalnet {
        start = a
        end = d
        a splits b + c + d
        b splits d
        c splits d
        a joins b
        a joins c
        a + b + c join d
    }

    @Test
    fun `subsumption 1a 2`() {
        val cmp = CausalNetTraceComparison(model1a, model2)
        assertTrue { cmp.leftSubsumedByRight }
        assertFalse { cmp.rightSubsumedByLeft }
        assertFalse { cmp.equivalent }
    }

    @Test
    fun `subsumption 1b 2`() {
        val cmp = CausalNetTraceComparison(model1b, model2)
        assertTrue { cmp.leftSubsumedByRight }
        assertFalse { cmp.rightSubsumedByLeft }
        assertFalse { cmp.equivalent }
    }

    @Test
    fun `different 1a 1b`() {
        val cmp = CausalNetTraceComparison(model1a, model1b)
        assertFalse { cmp.leftSubsumedByRight }
        assertFalse { cmp.rightSubsumedByLeft }
        assertFalse { cmp.equivalent }
    }

    @Test
    fun `equivalent 2 3`() {
        val cmp = CausalNetTraceComparison(model2, model3)
        assertTrue { cmp.leftSubsumedByRight }
        assertTrue { cmp.rightSubsumedByLeft }
        assertTrue { cmp.equivalent }
    }

    @Test
    fun `ignore special`() {
        assertTrue { CausalNetTraceComparison(model1a, model1c).equivalent }
        assertTrue { CausalNetTraceComparison(model1c, model1a).equivalent }
    }
}