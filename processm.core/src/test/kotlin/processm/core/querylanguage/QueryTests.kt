package processm.core.querylanguage

import org.antlr.v4.runtime.RecognitionException
import org.junit.jupiter.api.Tag
import kotlin.test.*

@Tag("PQL")
class QueryTests {
    @Test
    fun basicSelectTest() {
        val query = Query("select l:name, t:name, t:currency, e:name, e:total")
        assertFalse(query.selectAll)
        assertFalse(query.selectAllLog)
        assertFalse(query.selectAllTrace)
        assertFalse(query.selectAllEvent)
        // log scope
        assertEquals(1, query.selectLogStandardAttributes.size)
        assertEquals(0, query.selectLogOtherAttributes.size)
        assertEquals(0, query.selectLogExpressions.size)
        assertEquals("concept:name", query.selectLogStandardAttributes.elementAt(0).standardName)
        assertTrue(query.selectLogStandardAttributes.all { it.isStandard })
        assertTrue(query.selectLogStandardAttributes.all { it.scope == Scope.Log })
        assertTrue(query.selectLogStandardAttributes.all { !it.isClassifier })
        // trace scope
        assertEquals(2, query.selectTraceStandardAttributes.size)
        assertEquals(0, query.selectTraceOtherAttributes.size)
        assertEquals(0, query.selectTraceExpressions.size)
        assertEquals("concept:name", query.selectTraceStandardAttributes.elementAt(0).standardName)
        assertEquals("cost:currency", query.selectTraceStandardAttributes.elementAt(1).standardName)
        assertTrue(query.selectTraceStandardAttributes.all { it.isStandard })
        assertTrue(query.selectTraceStandardAttributes.all { it.scope == Scope.Trace })
        assertTrue(query.selectTraceStandardAttributes.all { !it.isClassifier })
        // event scope
        assertEquals(2, query.selectEventStandardAttributes.size)
        assertEquals(0, query.selectEventOtherAttributes.size)
        assertEquals(0, query.selectEventExpressions.size)
        assertEquals("concept:name", query.selectEventStandardAttributes.elementAt(0).standardName)
        assertEquals("cost:total", query.selectEventStandardAttributes.elementAt(1).standardName)
        assertTrue(query.selectEventStandardAttributes.all { it.isStandard })
        assertTrue(query.selectEventStandardAttributes.all { it.scope == Scope.Event })
        assertTrue(query.selectEventStandardAttributes.all { !it.isClassifier })
    }

    @Test
    fun scopedSelectAllTest() {
        val query = Query("select t:name, e:*, t:total, e:concept:name")
        assertFalse(query.selectAll)
        assertFalse(query.selectAllLog)
        assertFalse(query.selectAllTrace)
        assertTrue(query.selectAllEvent)
        // log scope
        assertEquals(0, query.selectLogStandardAttributes.size)
        assertEquals(0, query.selectLogOtherAttributes.size)
        assertEquals(0, query.selectLogExpressions.size)
        // trace scope
        assertEquals(2, query.selectTraceStandardAttributes.size)
        assertEquals(0, query.selectTraceOtherAttributes.size)
        assertEquals(0, query.selectTraceExpressions.size)
        assertEquals("concept:name", query.selectTraceStandardAttributes.elementAt(0).standardName)
        assertEquals("cost:total", query.selectTraceStandardAttributes.elementAt(1).standardName)
        assertTrue(query.selectTraceStandardAttributes.all { it.isStandard })
        assertTrue(query.selectTraceStandardAttributes.all { it.scope == Scope.Trace })
        assertTrue(query.selectTraceStandardAttributes.all { !it.isClassifier })
        // event scope
        assertEquals(0, query.selectEventStandardAttributes.size)
        assertEquals(0, query.selectEventOtherAttributes.size)
        assertEquals(0, query.selectEventExpressions.size)
    }

    @Test
    fun selectUsingClassifierTest() {
        val query = Query("select t:c:businesscase, e:classifier:activity_resource")
        assertFalse(query.selectAll)
        assertFalse(query.selectAllLog)
        assertFalse(query.selectAllTrace)
        assertFalse(query.selectAllEvent)
        // log scope
        assertEquals(0, query.selectLogStandardAttributes.size)
        assertEquals(0, query.selectLogOtherAttributes.size)
        assertEquals(0, query.selectLogExpressions.size)
        // trace scope
        assertEquals(1, query.selectTraceStandardAttributes.size)
        assertEquals(0, query.selectTraceOtherAttributes.size)
        assertEquals(0, query.selectTraceExpressions.size)
        assertEquals("classifier:businesscase", query.selectTraceStandardAttributes.elementAt(0).standardName)
        assertTrue(query.selectTraceStandardAttributes.all { it.isStandard })
        assertTrue(query.selectTraceStandardAttributes.all { it.scope == Scope.Trace })
        assertTrue(query.selectTraceStandardAttributes.all { it.isClassifier })
        // event scope
        assertEquals(1, query.selectEventStandardAttributes.size)
        assertEquals(0, query.selectEventOtherAttributes.size)
        assertEquals(0, query.selectEventExpressions.size)
        assertEquals("classifier:activity_resource", query.selectEventStandardAttributes.elementAt(0).standardName)
        assertTrue(query.selectEventStandardAttributes.all { it.isStandard })
        assertTrue(query.selectEventStandardAttributes.all { it.scope == Scope.Event })
        assertTrue(query.selectEventStandardAttributes.all { it.isClassifier })
    }

    @Test
    fun selectAggregationTest() {
        val query = Query("select min(t:total), avg(t:total), max(t:total)")
        assertFalse(query.selectAll)
        assertFalse(query.selectAllLog)
        assertFalse(query.selectAllTrace)
        assertFalse(query.selectAllEvent)
        // log scope
        assertEquals(0, query.selectLogStandardAttributes.size)
        assertEquals(0, query.selectLogOtherAttributes.size)
        assertEquals(0, query.selectLogExpressions.size)
        // trace scope
        assertEquals(0, query.selectTraceStandardAttributes.size)
        assertEquals(0, query.selectTraceOtherAttributes.size)
        assertEquals(3, query.selectTraceExpressions.size)
        assertEquals("min(trace:cost:total)", query.selectTraceExpressions.elementAt(0).toString())
        assertEquals("avg(trace:cost:total)", query.selectTraceExpressions.elementAt(1).toString())
        assertEquals("max(trace:cost:total)", query.selectTraceExpressions.elementAt(2).toString())
        assertTrue(query.selectTraceExpressions.all { !it.isTerminal })
        // event scope
        assertEquals(0, query.selectEventStandardAttributes.size)
        assertEquals(0, query.selectEventOtherAttributes.size)
        assertEquals(0, query.selectEventExpressions.size)
    }

    @Test
    fun selectNonStandardAttributesTest() {
        val query = Query("select [e:conceptowy:name], [e:time:timestamp], [org:resource2]")
        assertFalse(query.selectAll)
        assertFalse(query.selectAllLog)
        assertFalse(query.selectAllTrace)
        assertFalse(query.selectAllEvent)
        // log scope
        assertEquals(0, query.selectLogStandardAttributes.size)
        assertEquals(0, query.selectLogOtherAttributes.size)
        assertEquals(0, query.selectLogExpressions.size)
        // trace scope
        assertEquals(0, query.selectTraceStandardAttributes.size)
        assertEquals(0, query.selectTraceOtherAttributes.size)
        assertEquals(0, query.selectTraceExpressions.size)
        // event scope
        assertEquals(0, query.selectEventStandardAttributes.size)
        assertEquals(3, query.selectEventOtherAttributes.size)
        assertEquals(0, query.selectEventExpressions.size)
        assertEquals("conceptowy:name", query.selectEventOtherAttributes.elementAt(0).name)
        assertEquals("[event:conceptowy:name]", query.selectEventOtherAttributes.elementAt(0).toString())
        assertEquals("time:timestamp", query.selectEventOtherAttributes.elementAt(1).name)
        assertEquals("[event:time:timestamp]", query.selectEventOtherAttributes.elementAt(1).toString())
        assertEquals("org:resource2", query.selectEventOtherAttributes.elementAt(2).name)
        assertEquals("[event:org:resource2]", query.selectEventOtherAttributes.elementAt(2).toString())
        assertTrue(query.selectEventOtherAttributes.all { !it.isStandard })
        assertTrue(query.selectEventOtherAttributes.all { !it.isClassifier })
        assertTrue(query.selectEventOtherAttributes.all { it.isTerminal })
        assertTrue(query.selectEventOtherAttributes.all { it.scope == Scope.Event })
    }

    @Test
    fun selectExpressionTest() {
        val query = Query("select [e:conceptowy:name] + e:resource, max(timestamp) - \t \n min(timestamp)")
        assertFalse(query.selectAll)
        assertFalse(query.selectAllLog)
        assertFalse(query.selectAllTrace)
        assertFalse(query.selectAllEvent)
        // log scope
        assertEquals(0, query.selectLogStandardAttributes.size)
        assertEquals(0, query.selectLogOtherAttributes.size)
        assertEquals(0, query.selectLogExpressions.size)
        // trace scope
        assertEquals(0, query.selectTraceStandardAttributes.size)
        assertEquals(0, query.selectTraceOtherAttributes.size)
        assertEquals(0, query.selectTraceExpressions.size)
        // event scope
        assertEquals(0, query.selectEventStandardAttributes.size)
        assertEquals(0, query.selectEventOtherAttributes.size)
        assertEquals(2, query.selectEventExpressions.size)
        assertEquals("[event:conceptowy:name]+event:org:resource", query.selectEventExpressions.elementAt(0).toString())
        assertEquals(
            "max(event:time:timestamp)-min(event:time:timestamp)",
            query.selectEventExpressions.elementAt(1).toString()
        )
        assertTrue(query.selectEventExpressions.all { !it.isTerminal })
    }

    @Test
    fun selectAllImplicitTest() {
        val query = Query("")
        assertTrue(query.selectAll)
        assertTrue(query.selectAllLog)
        assertTrue(query.selectAllTrace)
        assertTrue(query.selectAllEvent)
        // log scope
        assertEquals(0, query.selectLogStandardAttributes.size)
        assertEquals(0, query.selectLogOtherAttributes.size)
        assertEquals(0, query.selectLogExpressions.size)
        // trace scope
        assertEquals(0, query.selectTraceStandardAttributes.size)
        assertEquals(0, query.selectTraceOtherAttributes.size)
        assertEquals(0, query.selectTraceExpressions.size)
        // event scope
        assertEquals(0, query.selectEventStandardAttributes.size)
        assertEquals(0, query.selectEventOtherAttributes.size)
        assertEquals(0, query.selectEventExpressions.size)
    }

    @Test
    @Ignore
    fun selectConstantsTest() {
        val query = Query("select l:1, l:2 + t:3, l:4 * t:5 + e:6, 7 / 8 - 9")
        assertTrue(query.selectAll)
        assertTrue(query.selectAllLog)
        assertTrue(query.selectAllTrace)
        assertTrue(query.selectAllEvent)
        // log scope
        assertEquals(0, query.selectLogStandardAttributes.size)
        assertEquals(0, query.selectLogOtherAttributes.size)
        assertEquals(1, query.selectLogExpressions.size)
        assertEquals("1", query.selectLogExpressions.elementAt(0).toString())
        assertTrue(query.selectLogExpressions.all { it.isTerminal })
        assertTrue(query.selectLogExpressions.all { it.effectiveScope == Scope.Log })
        // trace scope
        assertEquals(0, query.selectTraceStandardAttributes.size)
        assertEquals(0, query.selectTraceOtherAttributes.size)
        assertEquals(1, query.selectTraceExpressions.size)
        assertEquals("2+3", query.selectTraceExpressions.elementAt(0).toString())
        assertTrue(query.selectLogExpressions.all { !it.isTerminal })
        assertTrue(query.selectLogExpressions.all { it.effectiveScope == Scope.Trace })
        // event scope
        assertEquals(0, query.selectEventStandardAttributes.size)
        assertEquals(0, query.selectEventOtherAttributes.size)
        assertEquals(2, query.selectEventExpressions.size)
        assertEquals("4*5+6", query.selectEventExpressions.elementAt(0).toString())
        assertEquals("7/8-9", query.selectEventExpressions.elementAt(1).toString())
        assertTrue(query.selectLogExpressions.all { !it.isTerminal })
        assertTrue(query.selectLogExpressions.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectErrorHandlingTest() {
        val invalidSelects = listOf("select", "select *, *", "select * from log")

        invalidSelects.forEach {
            assertFailsWith<RecognitionException> { Query(it) }.apply {
                assertNotNull(message)
            }
        }

        val invalidAttributes =
            listOf("select tr:*, t:name", "select e:conceptowy:name", "select evant:*", "select evant:concept:name")
        invalidAttributes.forEach {
            assertFailsWith<NoSuchElementException> { Query(it) }.apply {
                assertNotNull(message)
            }
        }
    }
}