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
        assertTrue(query.selectLogStandardAttributes.all { it.effectiveScope == Scope.Log })
        assertTrue(query.selectLogStandardAttributes.all { !it.isClassifier })
        // trace scope
        assertEquals(2, query.selectTraceStandardAttributes.size)
        assertEquals(0, query.selectTraceOtherAttributes.size)
        assertEquals(0, query.selectTraceExpressions.size)
        assertEquals("concept:name", query.selectTraceStandardAttributes.elementAt(0).standardName)
        assertEquals("cost:currency", query.selectTraceStandardAttributes.elementAt(1).standardName)
        assertTrue(query.selectTraceStandardAttributes.all { it.isStandard })
        assertTrue(query.selectTraceStandardAttributes.all { it.effectiveScope == Scope.Trace })
        assertTrue(query.selectTraceStandardAttributes.all { !it.isClassifier })
        // event scope
        assertEquals(2, query.selectEventStandardAttributes.size)
        assertEquals(0, query.selectEventOtherAttributes.size)
        assertEquals(0, query.selectEventExpressions.size)
        assertEquals("concept:name", query.selectEventStandardAttributes.elementAt(0).standardName)
        assertEquals("cost:total", query.selectEventStandardAttributes.elementAt(1).standardName)
        assertTrue(query.selectEventStandardAttributes.all { it.isStandard })
        assertTrue(query.selectEventStandardAttributes.all { it.effectiveScope == Scope.Event })
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
        assertTrue(query.selectTraceStandardAttributes.all { it.effectiveScope == Scope.Trace })
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
        assertTrue(query.selectTraceStandardAttributes.all { it.effectiveScope == Scope.Trace })
        assertTrue(query.selectTraceStandardAttributes.all { it.isClassifier })
        // event scope
        assertEquals(1, query.selectEventStandardAttributes.size)
        assertEquals(0, query.selectEventOtherAttributes.size)
        assertEquals(0, query.selectEventExpressions.size)
        assertEquals("classifier:activity_resource", query.selectEventStandardAttributes.elementAt(0).standardName)
        assertTrue(query.selectEventStandardAttributes.all { it.isStandard })
        assertTrue(query.selectEventStandardAttributes.all { it.effectiveScope == Scope.Event })
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
        assertTrue(query.selectEventOtherAttributes.all { it.effectiveScope == Scope.Event })
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
    fun selectConstantsTest() {
        val query = Query("select l:1, l:2 + t:3, l:4 * t:5 + e:6, 7 / 8 - 9, 10 * null, t:null/11, l:D2020-03-12")
        assertFalse(query.selectAll)
        assertFalse(query.selectAllLog)
        assertFalse(query.selectAllTrace)
        assertFalse(query.selectAllEvent)
        // log scope
        assertEquals(0, query.selectLogStandardAttributes.size)
        assertEquals(0, query.selectLogOtherAttributes.size)
        assertEquals(2, query.selectLogExpressions.size)
        assertEquals("1.0", query.selectLogExpressions.elementAt(0).toString())
        assertEquals("2020-03-12T00:00:00Z", query.selectLogExpressions.elementAt(1).toString())
        assertTrue(query.selectLogExpressions.all { it.isTerminal })
        assertTrue(query.selectLogExpressions.all { it.effectiveScope == Scope.Log })
        // trace scope
        assertEquals(0, query.selectTraceStandardAttributes.size)
        assertEquals(0, query.selectTraceOtherAttributes.size)
        assertEquals(2, query.selectTraceExpressions.size)
        assertEquals("2.0+3.0", query.selectTraceExpressions.elementAt(0).toString())
        assertEquals("null/11.0", query.selectTraceExpressions.elementAt(1).toString())
        assertTrue(query.selectTraceExpressions.all { !it.isTerminal })
        assertTrue(query.selectTraceExpressions.all { it.effectiveScope == Scope.Trace })
        // event scope
        assertEquals(0, query.selectEventStandardAttributes.size)
        assertEquals(0, query.selectEventOtherAttributes.size)
        assertEquals(3, query.selectEventExpressions.size)
        assertEquals("4.0*5.0+6.0", query.selectEventExpressions.elementAt(0).toString())
        assertEquals("7.0/8.0-9.0", query.selectEventExpressions.elementAt(1).toString())
        assertEquals("10.0*null", query.selectEventExpressions.elementAt(2).toString())
        assertTrue(query.selectEventExpressions.all { !it.isTerminal })
        assertTrue(query.selectEventExpressions.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectISO8601Test() {
        val query = Query(
            """select 
                    D2020-03-13, 
                    D2020-03-13T16:45, 
                    D2020-03-13T16:45:50, 
                    D2020-03-13T16:45:50.333, 
                    D2020-03-13T16:45+0200, 
                    D2020-03-13T16:45+02:00,
                    D20200313, 
                    D20200313T1645, 
                    D20200313T164550, 
                    D20200313T164550.333, 
                    D20200313T1645+0200,
                    D202003131645, 
                    D20200313164550, 
                    D20200313164550.333, 
                    D202003131645+0200
                    """
        )
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
        assertEquals(15, query.selectEventExpressions.size)
        assertEquals("2020-03-13T00:00:00Z", query.selectEventExpressions.elementAt(0).toString())
        assertEquals("2020-03-13T16:45:00Z", query.selectEventExpressions.elementAt(1).toString())
        assertEquals("2020-03-13T16:45:50Z", query.selectEventExpressions.elementAt(2).toString())
        assertEquals("2020-03-13T16:45:50.333Z", query.selectEventExpressions.elementAt(3).toString())
        assertEquals("2020-03-13T14:45:00Z", query.selectEventExpressions.elementAt(4).toString())
        assertEquals("2020-03-13T14:45:00Z", query.selectEventExpressions.elementAt(5).toString())
        assertEquals("2020-03-13T00:00:00Z", query.selectEventExpressions.elementAt(6).toString())
        assertEquals("2020-03-13T16:45:00Z", query.selectEventExpressions.elementAt(7).toString())
        assertEquals("2020-03-13T16:45:50Z", query.selectEventExpressions.elementAt(8).toString())
        assertEquals("2020-03-13T16:45:50.333Z", query.selectEventExpressions.elementAt(9).toString())
        assertEquals("2020-03-13T14:45:00Z", query.selectEventExpressions.elementAt(10).toString())
        assertEquals("2020-03-13T16:45:00Z", query.selectEventExpressions.elementAt(11).toString())
        assertEquals("2020-03-13T16:45:50Z", query.selectEventExpressions.elementAt(12).toString())
        assertEquals("2020-03-13T16:45:50.333Z", query.selectEventExpressions.elementAt(13).toString())
        assertEquals("2020-03-13T14:45:00Z", query.selectEventExpressions.elementAt(14).toString())
        assertTrue(query.selectEventExpressions.all { it.isTerminal })
        assertTrue(query.selectEventExpressions.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectIEEE754Test() {
        val query = Query(
            "select 0, 0.0, 0.00, -0, -0.0, 1, 1.0, -1, -1.0, ${Math.PI}, ${Double.MIN_VALUE}, ${Double.MAX_VALUE}"
        )
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
        assertEquals(12, query.selectEventExpressions.size)
        assertEquals("0.0", query.selectEventExpressions.elementAt(0).toString())
        assertEquals("0.0", query.selectEventExpressions.elementAt(1).toString())
        assertEquals("0.0", query.selectEventExpressions.elementAt(2).toString())
        assertEquals("-0.0", query.selectEventExpressions.elementAt(3).toString())
        assertEquals("-0.0", query.selectEventExpressions.elementAt(4).toString())
        assertEquals("1.0", query.selectEventExpressions.elementAt(5).toString())
        assertEquals("1.0", query.selectEventExpressions.elementAt(6).toString())
        assertEquals("-1.0", query.selectEventExpressions.elementAt(7).toString())
        assertEquals("-1.0", query.selectEventExpressions.elementAt(8).toString())
        assertEquals(Math.PI.toString(), query.selectEventExpressions.elementAt(9).toString())
        assertEquals(Double.MIN_VALUE.toString(), query.selectEventExpressions.elementAt(10).toString())
        assertEquals(Double.MAX_VALUE.toString(), query.selectEventExpressions.elementAt(11).toString())
        assertTrue(query.selectEventExpressions.all { it.isTerminal })
        assertTrue(query.selectEventExpressions.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectBooleanTest() {
        val query = Query("select true, false")
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
        assertEquals("true", query.selectEventExpressions.elementAt(0).toString())
        assertEquals("false", query.selectEventExpressions.elementAt(1).toString())
        assertTrue(query.selectEventExpressions.all { it.isTerminal })
        assertTrue(query.selectEventExpressions.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectStringTest() {
        val query = Query("select 'single-quoted', \"double-quoted\"")
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
        assertEquals("single-quoted", query.selectEventExpressions.elementAt(0).toString())
        assertEquals("double-quoted", query.selectEventExpressions.elementAt(1).toString())
        assertTrue(query.selectEventExpressions.all { it.isTerminal })
        assertTrue(query.selectEventExpressions.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectErrorHandlingTest() {
        val invalidSelects =
            listOf(
                "select",
                "select *, *",
                "select * from log",
                "select tr:*, t:name",
                "select evant:*",
                "select evant:concept:name",
                "select ^l:*",
                "select ^l:concept:name",
                "select ^^t:concept:name",
                "select ^^^e:concept:name",
                "select ^^^name"
            )

        invalidSelects.forEach {
            assertFailsWith<RecognitionException> { Query(it) }.apply {
                assertNotNull(message)
            }
        }

        val invalidAttributes = listOf("select e:conceptowy:name", "select e:date")
        invalidAttributes.forEach {
            assertFailsWith<NoSuchElementException> { Query(it) }.apply {
                assertNotNull(message)
            }
        }

        val unsupportedOperations = listOf("select ^e:concept:name", "select avg(^time:timestamp)")
        unsupportedOperations.forEach {
            assertFailsWith<IllegalArgumentException> { Query(it) }.apply {
                assertNotNull(message)
            }
        }
    }

    @Test
    fun whereSimpleTest() {
        val query = Query("where dayofweek(e:timestamp) in (1, 7)")
        assertEquals("dayofweek(event:time:timestamp)in(1.0,7.0)", query.whereExpression.toString())
        assertEquals(Scope.Event, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereSimpleWithHoistingTest() {
        val query = Query("where dayofweek(^e:timestamp) in (1, 7)")
        assertEquals("dayofweek(^event:time:timestamp)in(1.0,7.0)", query.whereExpression.toString())
        assertEquals(Scope.Trace, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereSimpleWithHoistingTest2() {
        val query = Query("where dayofweek(^^e:timestamp) in (1, 7)")
        assertEquals("dayofweek(^^event:time:timestamp)in(1.0,7.0)", query.whereExpression.toString())
        assertEquals(Scope.Log, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLogicExprWithHoistingTest() {
        val query = Query("where not(t:currency = ^e:currency)")
        assertEquals("not(trace:cost:currency=^event:cost:currency)", query.whereExpression.toString())
        assertEquals(Scope.Trace, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLogicExprTest() {
        val query = Query("where t:currency != e:currency")
        assertEquals("trace:cost:currency!=event:cost:currency", query.whereExpression.toString())
        assertEquals(Scope.Event, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLogicExpr2Test() {
        val query = Query("where not(t:currency = ^e:currency) and t:total is null")
        assertEquals(
            "not(trace:cost:currency=^event:cost:currency)andtrace:cost:totalis null",
            query.whereExpression.toString()
        )
        assertEquals(Scope.Trace, query.whereExpression.effectiveScope)
    }
}