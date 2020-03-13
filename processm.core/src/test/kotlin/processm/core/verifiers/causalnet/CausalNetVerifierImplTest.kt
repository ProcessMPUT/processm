package processm.core.verifiers.causalnet

import processm.core.models.causalnet.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class CausalNetVerifierImplTest {


    @Test
    fun `Fig 3_12`() {
        val a = Node("register request")
        val b = Node("examine thoroughly")
        val c = Node("examine casually")
        val d = Node("check ticket")
        val e = Node("decide")
        val f = Node("reinitiate request")
        val g = Node("pay compensation")
        val h = Node("reject request")
        val z = Node("end")
        val mm = MutableModel(start = a, end = z)
        mm.addInstance(a, b, c, d, e, f, g, h, z)
        listOf(
            a to b, a to c, a to d, b to e, c to e, d to e, e to f, e to g,
            e to h, f to d, f to c, f to b, g to z, h to z
        ).forEach { mm.addDependency(it.first, it.second) }
        listOf(
            setOf(a to b, a to d),
            setOf(a to c, a to d),
            setOf(b to e),
            setOf(c to e),
            setOf(d to e),
            setOf(e to g),
            setOf(e to h),
            setOf(e to f),
            setOf(f to d, f to b),
            setOf(f to d, f to c),
            setOf(g to z),
            setOf(h to z)
        ).map { split -> split.map { Dependency(it.first, it.second) }.toSet() }
            .forEach { mm.addSplit(Split(it)) }
        listOf(
            setOf(a to b),
            setOf(f to b),
            setOf(a to c),
            setOf(f to c),
            setOf(a to d),
            setOf(f to d),
            setOf(b to e, d to e),
            setOf(c to e, d to e),
            setOf(e to f),
            setOf(e to g),
            setOf(e to h),
            setOf(g to z),
            setOf(h to z)
        ).map { join -> join.map { Dependency(it.first, it.second) }.toSet() }
            .forEach { mm.addJoin(Join(it)) }
        assertTrue { CausalNetVerifierImpl(mm).isSound }
    }

    @Test
    fun `Fig 3_13`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val model = MutableModel(start = a, end = e)
        model.addInstance(a, b, c, d, e)
        val ab = model.addDependency(a, b)
        val ac = model.addDependency(a, c)
        val ad = model.addDependency(a, d)
        val be = model.addDependency(b, e)
        val ce = model.addDependency(c, e)
        val de = model.addDependency(d, e)
        listOf(
            setOf(ab),
            setOf(ac),
            setOf(ab, ad),
            setOf(ac, ad),
            setOf(ab, ac, ad),
            setOf(be),
            setOf(ce),
            setOf(de)
        ).forEach { model.addSplit(Split(it)) }
        listOf(
            setOf(ab),
            setOf(ac),
            setOf(ad),
            setOf(be, ce, de),
            setOf(ce, de),
            setOf(be, de),
            setOf(be),
            setOf(ce)
        ).forEach { model.addJoin(Join(it)) }
        val v = CausalNetVerifierImpl(model)
        val seqs = v.validSequences
            .map { seq -> seq.map { ab -> ab.a } }
            .toSet()
        assertEquals(12, seqs.size)
        assertEquals(
            setOf(
                listOf(a, b, e),
                listOf(a, c, e),
                listOf(a, b, d, e),
                listOf(a, d, b, e),
                listOf(a, c, d, e),
                listOf(a, d, c, e),
                listOf(a, b, c, d, e),
                listOf(a, b, d, c, e),
                listOf(a, c, b, d, e),
                listOf(a, c, d, b, e),
                listOf(a, d, b, c, e),
                listOf(a, d, c, b, e)
            ), seqs
        )
        assertTrue { v.isSound }
    }

    private fun displayValidSequences(vs: Sequence<List<ActivityBinding>>) {
        vs.forEach { seq -> println(seq.map { ab -> ab.a.activity }) }
    }

    @Test
    fun `Fig 3_15_a`() {
        val a = Node("start booking")
        val b = Node("book flight")
        val c = Node("book car")
        val d = Node("book hotel")
        val e = Node("complete booking")
        val model = MutableModel(start = a, end = e)
        model.addInstance(a, b, c, d, e)
        val ab = model.addDependency(a, b)
        val ac = model.addDependency(a, c)
        val ad = model.addDependency(a, d)
        val be = model.addDependency(b, e)
        val ce = model.addDependency(c, e)
        val de = model.addDependency(d, e)
        listOf(
            setOf(ab),
            setOf(ac),
            setOf(ab, ad),
            setOf(ac, ad),
            setOf(ab, ac, ad),
            setOf(be),
            setOf(ce),
            setOf(de)
        ).forEach { model.addSplit(Split(it)) }
        listOf(
            setOf(ab),
            setOf(ac),
            setOf(ad),
            setOf(be, ce),
            setOf(de)
        ).forEach { model.addJoin(Join(it)) }
        val v = CausalNetVerifierImpl(model)
        assertTrue { v.validSequences.none() }
        assertFalse { v.isSound }
    }

    @Test
    fun `Fig 3_15_b`() {
        val a = Node("start booking")
        val b = Node("book flight")
        val c = Node("book car")
        val d = Node("book hotel")
        val e = Node("complete booking")
        val model = MutableModel(start = a, end = e)
        model.addInstance(a, b, c, d, e)
        val ab = model.addDependency(a, b)
        val ac = model.addDependency(a, c)
        val ad = model.addDependency(a, d)
        val be = model.addDependency(b, e)
        val ce = model.addDependency(c, e)
        val de = model.addDependency(d, e)
        listOf(
            setOf(ab),
            setOf(ac),
            setOf(ab, ad),
            setOf(ab, ac, ad),
            setOf(be),
            setOf(ce),
            setOf(de)
        ).forEach { model.addSplit(Split(it)) }
        listOf(
            setOf(ab),
            setOf(ac),
            setOf(ad),
            setOf(be, ce, de),
            setOf(ce, de),
            setOf(be, de),
            setOf(ce)
        ).forEach { model.addJoin(Join(it)) }
        val v = CausalNetVerifierImpl(model)
        assertFalse { v.isSound }
        assertTrue { v.validSequences.any() }
    }

    @Test
    fun `Fig 3_16`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val model = MutableModel(start = a, end = e)
        model.addInstance(a, b, c, d, e)
        val ab = model.addDependency(a, b)
        val bb = model.addDependency(b, b)
        val bc = model.addDependency(b, c)
        val bd = model.addDependency(b, d)
        val cd = model.addDependency(c, d)
        val dd = model.addDependency(d, d)
        val de = model.addDependency(d, e)
        listOf(
            setOf(ab),
            setOf(bb, bc),
            setOf(bc, bd),
            setOf(cd),
            setOf(dd),
            setOf(de)
        ).forEach { model.addSplit(Split(it)) }
        listOf(
            setOf(ab),
            setOf(bb),
            setOf(bc),
            setOf(cd, dd),
            setOf(bd, cd),
            setOf(de)
        ).forEach { model.addJoin(Join(it)) }
        val v = CausalNetVerifierImpl(model, useCache = false)
        assertTrue { v.isSound }
        val tmp = v.validSequences.map { seq -> seq.map { ab -> ab.a } }
        assertTrue { tmp.contains(listOf(a, b, b, c, c, d, d, e)) }
        assertTrue { tmp.contains(listOf(a, b, c, d, e)) }
        assertTrue { tmp.contains(listOf(a, b, c, b, c, d, d, e)) }
        assertEquals(
            setOf(
                listOf(a, b, c, d, e),
                listOf(a, b, b, c, c, d, d, e)
            ),
            v.validLoopFreeSequences.map { seq -> seq.map { ab -> ab.a }.sortedBy { it.activity } }.toSet()
        )
    }

    @Test
    fun `empty model`() {
        val model = MutableModel()
        val v = CausalNetVerifierImpl(model)
        assertFalse { v.isSound }
        assertTrue { v.validSequences.none() }
    }
}