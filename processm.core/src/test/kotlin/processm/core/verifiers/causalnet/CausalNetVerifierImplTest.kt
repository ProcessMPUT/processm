package processm.core.verifiers.causalnet

import processm.core.helpers.mapToSet
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
        val mm = MutableCausalNet(start = a, end = z)
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
        ).map { split -> split.mapToSet { Dependency(it.first, it.second) } }
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
        ).map { join -> join.mapToSet { Dependency(it.first, it.second) } }
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
        val model = MutableCausalNet(start = a, end = e)
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
        v.validSequences.forEach { seq -> assertTrue { v.isValid(seq) } }
        v.validSequences.forEach { seq -> assertFalse { v.isValid(seq.subList(1, seq.size)) } }
        v.validSequences.forEach { seq -> assertFalse { v.isValid(seq.subList(0, seq.size - 1)) } }
        v.validSequences.forEach { seq -> assertFalse { v.isValid(seq.subList(0, 1) + seq.subList(seq.size-1, seq.size)) } }
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
        val model = MutableCausalNet(start = a, end = e)
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
        val model = MutableCausalNet(start = a, end = e)
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

    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")

    @Test
    fun `Fig 3_16`() {
        val model = MutableCausalNet(start = a, end = e)
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
            v.validLoopFreeSequences.mapToSet { seq -> seq.map { ab -> ab.a }.sortedBy { it.activity } }
        )
    }

    @Test
    fun `empty model`() {
        val model = MutableCausalNet()
        val v = CausalNetVerifierImpl(model)
        assertFalse { v.isSound }
        assertTrue { v.validSequences.none() }
    }

    @Test
    fun `sequences with arbitrary serialization in presence of paralleism`() {
        val model = causalnet {
            start = a
            end = d
            a splits b + c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c join d
        }
        assertEquals(1, CausalNetVerifierImpl(model).validLoopFreeSequencesWithArbitrarySerialization.count())
    }

    @Test
    fun `sequences with arbitrary serialization in absence of paralleism`() {
        val model = causalnet {
            start = a
            end = d
            a splits b or c
            b splits d
            c splits d
            a joins b
            a joins c
            b or c join d
        }
        assertEquals(2, CausalNetVerifierImpl(model).validLoopFreeSequencesWithArbitrarySerialization.count())
    }

    @Test
    fun `single L2 loop`() {
        val model = causalnet {
            start splits a
            a splits b
            b splits a or end
            start or b join a
            a joins b
            b joins end
        }
        assertTrue(CausalNetVerifierImpl(model).noDeadParts)
    }

    @Test
    fun connectivity() {
        val model = MutableCausalNet(start = a, end = d)
        model.addInstance(a, b, c, d)
        model.addDependency(a, b)
        model.addDependency(a, c)
        val v1 = CausalNetVerifierImpl(model)
        assertFalse(v1.isEveryNodeReachableFromStart)
        assertFalse(v1.isEndReachableFromEveryNode)
        assertFalse(v1.isConnected)
        model.addDependency(c, d)
        val v2 = CausalNetVerifierImpl(model)
        assertTrue(v2.isEveryNodeReachableFromStart)
        assertFalse(v2.isEndReachableFromEveryNode)
        assertFalse(v2.isConnected)
        model.addDependency(b, d)
        val v3 = CausalNetVerifierImpl(model)
        assertTrue(v3.isEveryNodeReachableFromStart)
        assertTrue(v3.isEndReachableFromEveryNode)
        assertTrue(v3.isConnected)
    }

    @Test
    fun `structure - ok`() {
        val model = causalnet {
            start = a
            end = c
            a splits b
            b splits c
            a joins b
            b joins c
        }
        assertTrue { CausalNetVerifierImpl(model).isStructurallySound }
    }

    @Test
    fun `structure - unused dependency`() {
        val model = causalnet {
            start = a
            end = c
            a splits b
            b splits c
            a joins b
            b joins c
        }
        model.addDependency(a, c)
        assertFalse { CausalNetVerifierImpl(model).isStructurallySound }
    }

    @Test
    fun `structure - two starts`() {
        val model = causalnet {
            start = a
            end = c
            a splits b
            b splits c
            a joins b
            b joins c
        }
        model.addInstance(d)
        val dep = model.addDependency(d, b)
        model.addSplit(Split(setOf(dep)))
        model.addJoin(Join(setOf(dep)))
        assertFalse { CausalNetVerifierImpl(model).isStructurallySound }
    }

    @Test
    fun `structure - two ends`() {
        val model = causalnet {
            start = a
            end = c
            a splits b
            b splits c
            a joins b
            b joins c
        }
        model.addInstance(d)
        val dep = model.addDependency(b, d)
        model.addSplit(Split(setOf(dep)))
        model.addJoin(Join(setOf(dep)))
        assertFalse { CausalNetVerifierImpl(model).isStructurallySound }
    }

    @Test
    fun `structure - missing join`() {
        val model = causalnet {
            start = a
            end = c
            a splits b
            b splits c
            a joins b
        }
        assertEquals(setOf(Dependency(b, c)), CausalNetVerifierImpl(model).dependenciesUnusedInJoins)
    }

    @Test
    fun `structure - missing split`() {
        val model = causalnet {
            start = a
            end = c
            a splits b
            a joins b
            b joins c
        }
        assertEquals(setOf(Dependency(b, c)), CausalNetVerifierImpl(model).dependenciesUnusedInSplits)
    }
}