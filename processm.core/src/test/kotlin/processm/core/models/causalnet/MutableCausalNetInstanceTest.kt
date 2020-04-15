package processm.core.models.causalnet

import kotlin.test.*

class MutableCausalNetInstanceTest {

    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
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
    lateinit var instance: MutableCausalNetInstance

    @BeforeTest
    fun before() {
        instance = model.createInstance()
    }

    @Test
    fun `execute correct trace 1`() {
        assertTrue { instance.state.isEmpty() }
        assertEquals(setOf(a), instance.availableActivities.toSet())
        instance.execute(null, model.splits.getValue(a).first())
        assertEquals(setOf(b, c), instance.availableActivities.toSet())
        instance.execute(model.joins.getValue(b).first(), model.splits.getValue(b).first())
        assertEquals(setOf(c), instance.availableActivities.toSet())
        instance.execute(model.joins.getValue(c).first(), model.splits.getValue(c).first())
        assertEquals(setOf(d), instance.availableActivities.toSet())
        instance.execute(model.joins.getValue(d).first(), null)
        assertTrue { instance.state.isEmpty() }
    }

    @Test
    fun `execute correct trace 2`() {
        assertTrue { instance.state.isEmpty() }
        assertEquals(setOf(a), instance.availableActivities.toSet())
        instance.execute(null, model.splits.getValue(a).first())
        assertEquals(setOf(b, c), instance.availableActivities.toSet())
        instance.execute(model.joins.getValue(c).first(), model.splits.getValue(c).first())
        assertEquals(setOf(b), instance.availableActivities.toSet())
        instance.execute(model.joins.getValue(b).first(), model.splits.getValue(b).first())
        assertEquals(setOf(d), instance.availableActivities.toSet())
        instance.execute(model.joins.getValue(d).first(), null)
        assertTrue { instance.state.isEmpty() }
    }

    @Test
    fun `execute not ready activity`() {
        assertTrue { instance.state.isEmpty() }
        instance.execute(null, model.splits.getValue(a).first())
        assertFailsWith<IllegalStateException> { instance.execute(model.joins.getValue(d).first(), null) }
    }

    @Test
    fun `join to one split from another`() {
        assertTrue { instance.state.isEmpty() }
        assertEquals(setOf(a), instance.availableActivities.toSet())
        instance.execute(null, model.splits.getValue(a).first())
        assertEquals(setOf(b, c), instance.availableActivities.toSet())
        assertFailsWith<IllegalArgumentException> {
            instance.execute(
                model.joins.getValue(c).first(),
                model.splits.getValue(b).first()
            )
        }
    }

    @Test
    fun `two nulls`() {
        assertFailsWith<IllegalArgumentException> { instance.execute(null, null) }
    }

    @Test
    fun `non-existing bindings`() {
        instance.execute(null, model.splits.getValue(a).first())
        assertFailsWith<IllegalArgumentException> { instance.execute(Join(setOf(Dependency(a,d))), null) }
    }

    @Test
    fun `execute correct trace 1 using abstract interface`() {
        assertTrue { instance.state.isEmpty() }
        assertEquals(setOf(a), instance.availableActivities.toSet())
        instance.availableActivityExecutions.single().execute()
        assertEquals(setOf(b, c), instance.availableActivities.toSet())
        instance.availableActivityExecutions.filter { it.activity==b }.single().execute()
        assertEquals(setOf(c), instance.availableActivities.toSet())
        instance.availableActivityExecutions.single().execute()
        assertEquals(setOf(d), instance.availableActivities.toSet())
        instance.availableActivityExecutions.single().execute()
        assertTrue { instance.state.isEmpty() }
    }
}