package processm.core.models.causalnet

import processm.core.DBTestHelper.dbName
import processm.core.models.metadata.BasicMetadata
import processm.core.models.metadata.DefaultMetadataProvider
import processm.core.models.metadata.SingleDoubleMetadata
import processm.core.persistence.connection.DBCache
import processm.helpers.mapToSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DBSerializerTest {
    //activities inspired by Fig 3.12 in "Process Mining" by Van van der Alst
    private val a = Node("register request")
    private val b = Node("examine thoroughly")
    private val c = Node("examine casually")
    private val d = Node("check ticket")
    private val e = Node("decide")
    private val f = Node("reinitiate request")
    private val g = Node("pay compensation")
    private val h = Node("reject request")
    private val z = Node("end")

    //constructing model represented at Fig 3.12 in "Process Mining" by Wil van der Aalst
    @Test
    fun `insert fetch and compare`() {
        var mm = MutableCausalNet(start = a, end = z)
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
        val id = DBSerializer.insert(DBCache.get(dbName).database, mm)
        val fetched = DBSerializer.fetch(DBCache.get(dbName).database, id)
        assertEquals(mm.start, fetched.start)
        assertEquals(mm.end, fetched.end)
        assertEquals(mm.instances, fetched.instances)
        assertEquals(mm.incoming, fetched.incoming)
        assertEquals(mm.outgoing, fetched.outgoing)
        assertEquals(mm.joins, fetched.joins)
        assertEquals(mm.splits, fetched.splits)
    }

    @Test
    fun `special nodes handling`() {
        val orig = MutableCausalNet()
        val id = DBSerializer.insert(DBCache.get(dbName).database, orig)
        val copy = DBSerializer.fetch(DBCache.get(dbName).database, id)
        assertEquals(orig.instances, copy.instances)
        assertEquals(orig.start, copy.start)
        assertEquals(orig.start, copy.start)
        assertTrue(copy.start.isSilent)
        assertTrue(copy.end.isSilent)
    }

    @Test
    fun `metadata handling`() {
        val orig = MutableCausalNet()
        val dep = orig.addDependency(orig.start, orig.end)
        val mp = DefaultMetadataProvider<SingleDoubleMetadata>(BasicMetadata.DEPENDENCY_MEASURE)
        mp.put(dep, SingleDoubleMetadata(13.37))
        orig.addMetadataProvider(mp)
        val id = DBSerializer.insert(DBCache.get(dbName).database, orig)
        val copy = DBSerializer.fetch(DBCache.get(dbName).database, id)
        assertEquals(orig.instances, copy.instances)
        assertEquals(orig.start, copy.start)
        assertEquals(orig.start, copy.start)
        assertTrue(copy.start.isSilent)
        assertTrue(copy.end.isSilent)
        assertTrue { BasicMetadata.DEPENDENCY_MEASURE in copy.availableMetadata }
        assertEquals(
            13.37,
            (copy.getMetadata(copy.dependencies.single(), BasicMetadata.DEPENDENCY_MEASURE) as SingleDoubleMetadata)
                .value
        )
    }

    @Test
    fun `insert fetch delete fetch`() {
        val mm = MutableCausalNet()
        val id = DBSerializer.insert(DBCache.get(dbName).database, mm)
        DBSerializer.fetch(DBCache.get(dbName).database, id)
        DBSerializer.delete(DBCache.get(dbName), id)
        assertFailsWith<NoSuchElementException> { DBSerializer.fetch(DBCache.get(dbName).database, id) }
    }

    @Test
    fun `fetch nonexisting model`() {
        assertFailsWith<NoSuchElementException> { DBSerializer.fetch(DBCache.get(dbName).database, -1) }
    }

    @Test
    fun `delete nonexisting model`() {
        assertFailsWith<NoSuchElementException> { DBSerializer.delete(DBCache.get(dbName), -1) }
    }
}
