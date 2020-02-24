package processm.core.models.casualnet

import org.junit.Test
import processm.core.models.causalnet.*
import java.lang.IllegalArgumentException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutableModelTest {

    //activities inspired by Fig 3.12 in "Process Mining" by Van van der Alst
    private val a = ActivityInstance(Activity("register request"))
    private val b = ActivityInstance(Activity("examine thoroughly"))
    private val c = ActivityInstance(Activity("examine casually"))
    private val d = ActivityInstance(Activity("check ticket"))
    private val e = ActivityInstance(Activity("decide"))
    private val f = ActivityInstance(Activity("reinitiate request"))
    private val g = ActivityInstance(Activity("pay compensation"))
    private val h = ActivityInstance(Activity("reject request"))
    private val z = ActivityInstance(Activity("end"))

    //constructing model represented at Fig 3.12 in "Process Mining" by Van van der Alst
    @Test
    fun constructModel() {
        var mm = MutableModel(start = a, end = z)
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
        assertEquals(setOf(a, b, c, d, e, f, g, h, z), mm.instances)
        assertTrue(mm.outgoing[a]?.all { it.source == a } == true)
        assertEquals(setOf(b, c, d), mm.outgoing[a]?.map { it.target }?.toSet())
        assertFalse { z in mm.outgoing }
        assertTrue { mm.incoming[z]?.all { it.target == z } == true }
        assertEquals(setOf(g, h), mm.incoming[z]?.map { it.source }?.toSet())
        assertEquals(2, mm.splits[a]?.size)
        assertEquals(1, mm.splits[b]?.size)
        assertFalse { z in mm.splits }
        assertEquals(2, mm.joins[e]?.size)
        assertEquals(1, mm.joins[f]?.size)
        assertEquals(setOf(Dependency(e, f)), mm.joins[f]?.first()?.dependencies)
        assertFalse { a in mm.joins }
    }

    @Test(expected = IllegalArgumentException::class)
    fun addDependencyWithUnknownTarget() {
        val mm = MutableModel()
        mm.addInstance(a)
        mm.addDependency(a, b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun addDependencyWithUnknownSource() {
        val mm = MutableModel()
        mm.addInstance(b)
        mm.addDependency(Dependency(a, b))
    }

    @Test(expected = NoSuchElementException::class)
    fun addSplitWithNoInstance() {
        val mm = MutableModel()
        mm.addInstance(a)
        mm.addSplit(Split(setOf(Dependency(a, b))))
    }

    @Test(expected = NoSuchElementException::class)
    fun addSplitWithNoDependency() {
        val mm = MutableModel()
        mm.addInstance(a, b)
        mm.addSplit(Split(setOf(Dependency(a, b))))
    }


    @Test(expected = NoSuchElementException::class)
    fun addJoinWithNoInstance() {
        val mm = MutableModel()
        mm.addInstance(a)
        mm.addJoin(Join(setOf(Dependency(a, b))))
    }

    @Test(expected = NoSuchElementException::class)
    fun addJoinWithNoDependency() {
        val mm = MutableModel()
        mm.addInstance(a, b)
        mm.addJoin(Join(setOf(Dependency(a, b))))
    }

    @Test
    fun defaultStartAndEnd() {
        val mm = MutableModel()
        assertTrue { mm.start in mm.instances }
        assertTrue { mm.end in mm.instances }
        assertTrue { mm.start.activity.special }
        assertTrue { mm.end.activity.special }
    }
}