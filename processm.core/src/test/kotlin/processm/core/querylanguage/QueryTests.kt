package processm.core.querylanguage

import org.antlr.v4.runtime.RecognitionException
import org.junit.jupiter.api.Tag
import kotlin.test.*

@Tag("PQL")
class QueryTests {
    @Test
    fun basicSelectTest() {
        val query = Query("select l:name, t:name, t:currency, e:name, e:total")
        assertFalse(query.isImplicitSelectAll)
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
        assertFalse(query.isImplicitSelectAll)
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

        assertNotNull(query.warning)
        assertTrue("select all" in query.warning!!.message!!)
    }

    @Test
    fun selectUsingClassifierTest() {
        val query = Query("select t:c:businesscase, e:classifier:activity_resource")
        assertFalse(query.isImplicitSelectAll)
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
        assertFalse(query.isImplicitSelectAll)
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
        assertFalse(query.isImplicitSelectAll)
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
        val query = Query(
            "select [e:conceptowy:name] + e:resource, max(timestamp) - \t \n min(timestamp)" +
                    "group event by [e:conceptowy:name], e:resource"
        )
        assertFalse(query.isImplicitSelectAll)
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
        assertTrue(query.isImplicitSelectAll)
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
        assertFalse(query.isImplicitSelectAll)
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
                    D2020-03-13T16:45Z,
                    D20200313, 
                    D20200313T1645, 
                    D20200313T164550, 
                    D20200313T164550.333, 
                    D20200313T1645+0200,
                    D202003131645, 
                    D20200313164550, 
                    D20200313164550.333, 
                    D202003131645+0200,
                    D202003131645Z
                    """
        )
        assertFalse(query.isImplicitSelectAll)
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
        assertEquals(17, query.selectEventExpressions.size)
        assertEquals("2020-03-13T00:00:00Z", query.selectEventExpressions.elementAt(0).toString())
        assertEquals("2020-03-13T16:45:00Z", query.selectEventExpressions.elementAt(1).toString())
        assertEquals("2020-03-13T16:45:50Z", query.selectEventExpressions.elementAt(2).toString())
        assertEquals("2020-03-13T16:45:50.333Z", query.selectEventExpressions.elementAt(3).toString())
        assertEquals("2020-03-13T14:45:00Z", query.selectEventExpressions.elementAt(4).toString())
        assertEquals("2020-03-13T14:45:00Z", query.selectEventExpressions.elementAt(5).toString())
        assertEquals("2020-03-13T16:45:00Z", query.selectEventExpressions.elementAt(6).toString())
        assertEquals("2020-03-13T00:00:00Z", query.selectEventExpressions.elementAt(7).toString())
        assertEquals("2020-03-13T16:45:00Z", query.selectEventExpressions.elementAt(8).toString())
        assertEquals("2020-03-13T16:45:50Z", query.selectEventExpressions.elementAt(9).toString())
        assertEquals("2020-03-13T16:45:50.333Z", query.selectEventExpressions.elementAt(10).toString())
        assertEquals("2020-03-13T14:45:00Z", query.selectEventExpressions.elementAt(11).toString())
        assertEquals("2020-03-13T16:45:00Z", query.selectEventExpressions.elementAt(12).toString())
        assertEquals("2020-03-13T16:45:50Z", query.selectEventExpressions.elementAt(13).toString())
        assertEquals("2020-03-13T16:45:50.333Z", query.selectEventExpressions.elementAt(14).toString())
        assertEquals("2020-03-13T14:45:00Z", query.selectEventExpressions.elementAt(15).toString())
        assertEquals("2020-03-13T16:45:00Z", query.selectEventExpressions.elementAt(16).toString())
        assertTrue(query.selectEventExpressions.all { it.isTerminal })
        assertTrue(query.selectEventExpressions.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectIEEE754Test() {
        val query = Query(
            "select 0, 0.0, 0.00, -0, -0.0, 1, 1.0, -1, -1.0, ${Math.PI}, ${Double.MIN_VALUE}, ${Double.MAX_VALUE}"
        )
        assertFalse(query.isImplicitSelectAll)
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
        assertFalse(query.isImplicitSelectAll)
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
        assertFalse(query.isImplicitSelectAll)
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
    fun errorHandlingTest() {
        val invalidSyntax =
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
                "select ^^^name",
                "limit l:${Double.NaN}",
                "limit l:${Double.POSITIVE_INFINITY}",
                "limit l:${Double.NEGATIVE_INFINITY}",
                "offset e:${Double.NaN}",
                "offset e:${Double.POSITIVE_INFINITY}",
                "offset e:${Double.NEGATIVE_INFINITY}"
            )

        invalidSyntax.forEach {
            assertFailsWith<RecognitionException>(it) { Query(it) }.apply {
                assertNotNull(message)
            }
        }

        val invalidAttributes = listOf(
            "select e:conceptowy:name",
            "select e:date",
            "select t:timestamp",
            "select e:t:timestamp"
        )
        invalidAttributes.forEach {
            assertFailsWith<NoSuchElementException>(it) { Query(it) }.apply {
                assertNotNull(message)
            }
        }

        val illegalOperations = listOf(
            "select ^e:concept:name",
            "select e:timestamp group by e:name",
            "group by e:name order by e:timestamp",
            "select e:total + 10 group by e:name",
            "select avg(e:total), e:resource",
            "where avg(e:total) > 100",
            "limit 42",
            "limit l:-1",
            "limit l:0",
            "limit l:0.1",
            "limit l:-0.01",
            "offset 42",
            "offset l:-1",
            "offset l:0"
        )
        illegalOperations.forEach {
            assertFailsWith<IllegalArgumentException>(it) { Query(it) }.apply {
                assertNotNull(message)
            }
        }
    }

    @Test
    fun whereSimpleTest() {
        val query = Query("where dayofweek(e:timestamp) in (1, 7)")
        assertTrue(query.isImplicitSelectAll)
        assertTrue(query.selectAll)
        assertEquals("dayofweek(event:time:timestamp)in(1.0,7.0)", query.whereExpression.toString())
        assertEquals(Scope.Event, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereSimpleWithHoistingTest() {
        val query = Query("where dayofweek(^e:timestamp) in (1, 7)")
        assertTrue(query.isImplicitSelectAll)
        assertTrue(query.selectAll)
        assertEquals("dayofweek(^event:time:timestamp)in(1.0,7.0)", query.whereExpression.toString())
        assertEquals(Scope.Trace, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereSimpleWithHoistingTest2() {
        val query = Query("where dayofweek(^^e:timestamp) in (1, 7)")
        assertTrue(query.isImplicitSelectAll)
        assertTrue(query.selectAll)
        assertEquals("dayofweek(^^event:time:timestamp)in(1.0,7.0)", query.whereExpression.toString())
        assertEquals(Scope.Log, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLogicExprWithHoistingTest() {
        val query = Query("where not(t:currency = ^e:currency)")
        assertTrue(query.isImplicitSelectAll)
        assertTrue(query.selectAll)
        assertEquals("not(trace:cost:currency=^event:cost:currency)", query.whereExpression.toString())
        assertEquals(Scope.Trace, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLogicExprTest() {
        val query = Query("where t:currency != e:currency")
        assertTrue(query.isImplicitSelectAll)
        assertTrue(query.selectAll)
        assertEquals("trace:cost:currency!=event:cost:currency", query.whereExpression.toString())
        assertEquals(Scope.Event, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLogicExpr2Test() {
        val query = Query("where not(t:currency = ^e:currency) and t:total is null")
        assertTrue(query.isImplicitSelectAll)
        assertTrue(query.selectAll)
        assertEquals(
            "not(trace:cost:currency=^event:cost:currency)andtrace:cost:totalis null",
            query.whereExpression.toString()
        )
        assertEquals(Scope.Trace, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLogicExpr3Test() {
        val query = Query("where (not(t:currency = ^e:currency) or ^e:timestamp >= D2020-01-01) and t:total is null")
        assertTrue(query.isImplicitSelectAll)
        assertTrue(query.selectAll)
        assertEquals(
            "(not(trace:cost:currency=^event:cost:currency)or^event:time:timestamp>=2020-01-01T00:00:00Z)andtrace:cost:totalis null",
            query.whereExpression.toString()
        )
        assertEquals(Scope.Trace, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLikeAndMatchesTest() {
        val query = Query("where t:name like 'transaction %' and ^e:resource matches '^[A-Z][a-z]+ [A-Z][a-z]+$'")
        assertTrue(query.selectAll)
        assertEquals(
            "trace:concept:nameliketransaction %and^event:org:resourcematches^[A-Z][a-z]+ [A-Z][a-z]+\$",
            query.whereExpression.toString()
        )

        assertNull(query.warning)
    }

    @Test
    fun groupScopeByClassifierTest() {
        val query = Query("group trace by e:classifier:activity")
        assertTrue(query.isImplicitSelectAll)
        assertTrue(query.selectAll)
        assertFalse(query.isImplicitGroupBy)
        assertFalse(query.isGroupLogBy)
        assertTrue(query.isGroupTraceBy)
        assertFalse(query.isGroupEventBy)
        assertEquals(0, query.groupLogByStandardAttributes.size)
        assertEquals(1, query.groupTraceByStandardAttributes.size)
        assertEquals(0, query.groupEventByStandardAttributes.size)
        assertEquals(0, query.groupLogByOtherAttributes.size)
        assertEquals(0, query.groupTraceByOtherAttributes.size)
        assertEquals(0, query.groupEventByOtherAttributes.size)
        assertTrue(query.groupTraceByStandardAttributes.all { it.isStandard })
        assertTrue(query.groupTraceByStandardAttributes.all { it.isClassifier })
        assertEquals("classifier:activity", query.groupTraceByStandardAttributes.elementAt(0).standardName)
        assertEquals(Scope.Event, query.groupTraceByStandardAttributes.elementAt(0).effectiveScope)
    }

    @Test
    fun groupEventByStandardAttributeTest() {
        val query = Query(
            """select t:name, e:name, sum(e:total)
            group event by e:name"""
        )
        assertFalse(query.isImplicitSelectAll)
        assertFalse(query.selectAll)
        assertFalse(query.isImplicitGroupBy)
        assertFalse(query.isGroupLogBy)
        assertFalse(query.isGroupTraceBy)
        assertTrue(query.isGroupEventBy)
        assertEquals("concept:name", query.selectTraceStandardAttributes.elementAt(0).standardName)
        assertEquals("concept:name", query.selectEventStandardAttributes.elementAt(0).standardName)
        assertEquals("sum(event:cost:total)", query.selectEventExpressions.elementAt(0).toString())
        assertEquals(0, query.groupLogByStandardAttributes.size)
        assertEquals(0, query.groupTraceByStandardAttributes.size)
        assertEquals(1, query.groupEventByStandardAttributes.size)
        assertEquals(0, query.groupLogByOtherAttributes.size)
        assertEquals(0, query.groupTraceByOtherAttributes.size)
        assertEquals(0, query.groupEventByOtherAttributes.size)
        assertTrue(query.groupEventByStandardAttributes.all { it.isStandard })
        assertTrue(query.groupEventByStandardAttributes.all { !it.isClassifier })
        assertEquals("concept:name", query.groupEventByStandardAttributes.elementAt(0).standardName)
        assertEquals(Scope.Event, query.groupEventByStandardAttributes.elementAt(0).effectiveScope)
    }

    @Test
    fun groupTraceByEventStandardAttributeTest() {
        val query = Query(
            """select e:name, sum(e:total)
            group trace by e:name"""
        )
        assertFalse(query.isImplicitSelectAll)
        assertFalse(query.selectAll)
        assertFalse(query.isImplicitGroupBy)
        assertFalse(query.isGroupLogBy)
        assertTrue(query.isGroupTraceBy)
        assertFalse(query.isGroupEventBy)
        assertEquals("concept:name", query.selectEventStandardAttributes.elementAt(0).standardName)
        assertEquals("sum(event:cost:total)", query.selectEventExpressions.elementAt(0).toString())
        assertEquals(0, query.groupLogByStandardAttributes.size)
        assertEquals(1, query.groupTraceByStandardAttributes.size)
        assertEquals(0, query.groupEventByStandardAttributes.size)
        assertEquals(0, query.groupLogByOtherAttributes.size)
        assertEquals(0, query.groupTraceByOtherAttributes.size)
        assertEquals(0, query.groupEventByOtherAttributes.size)
        assertTrue(query.groupTraceByStandardAttributes.all { it.isStandard })
        assertTrue(query.groupTraceByStandardAttributes.all { !it.isClassifier })
        assertEquals("concept:name", query.groupTraceByStandardAttributes.elementAt(0).standardName)
        assertEquals(Scope.Event, query.groupTraceByStandardAttributes.elementAt(0).effectiveScope)
    }

    @Test
    fun groupLogByEventStandardAttributeTest() {
        val query = Query(
            """select e:name, sum(e:total)
            group log by e:name"""
        )
        assertFalse(query.isImplicitSelectAll)
        assertFalse(query.selectAll)
        assertFalse(query.isImplicitGroupBy)
        assertTrue(query.isGroupLogBy)
        assertFalse(query.isGroupTraceBy)
        assertFalse(query.isGroupEventBy)
        assertEquals("concept:name", query.selectEventStandardAttributes.elementAt(0).standardName)
        assertEquals("sum(event:cost:total)", query.selectEventExpressions.elementAt(0).toString())
        assertEquals(1, query.groupLogByStandardAttributes.size)
        assertEquals(0, query.groupTraceByStandardAttributes.size)
        assertEquals(0, query.groupEventByStandardAttributes.size)
        assertEquals(0, query.groupLogByOtherAttributes.size)
        assertEquals(0, query.groupTraceByOtherAttributes.size)
        assertEquals(0, query.groupEventByOtherAttributes.size)
        assertTrue(query.groupLogByStandardAttributes.all { it.isStandard })
        assertTrue(query.groupLogByStandardAttributes.all { !it.isClassifier })
        assertEquals("concept:name", query.groupLogByStandardAttributes.elementAt(0).standardName)
        assertEquals(Scope.Event, query.groupLogByStandardAttributes.elementAt(0).effectiveScope)
    }

    @Test
    fun groupByImplicitScopeTest() {
        val query = Query("group by e:c:main, [t:branch]")
        assertTrue(query.isImplicitSelectAll)
        assertTrue(query.selectAll)
        assertFalse(query.isImplicitGroupBy)
        assertFalse(query.isGroupLogBy)
        assertTrue(query.isGroupTraceBy)
        assertFalse(query.isGroupEventBy)
        assertEquals(0, query.groupLogByStandardAttributes.size)
        assertEquals(1, query.groupTraceByStandardAttributes.size)
        assertEquals(0, query.groupEventByStandardAttributes.size)
        assertEquals(0, query.groupLogByOtherAttributes.size)
        assertEquals(1, query.groupTraceByOtherAttributes.size)
        assertEquals(0, query.groupEventByOtherAttributes.size)
        assertTrue(query.groupTraceByStandardAttributes.all { it.isStandard })
        assertTrue(query.groupTraceByStandardAttributes.all { it.isClassifier })
        assertEquals("classifier:main", query.groupTraceByStandardAttributes.elementAt(0).standardName)
        assertEquals(Scope.Event, query.groupTraceByStandardAttributes.elementAt(0).effectiveScope)
        assertTrue(query.groupTraceByOtherAttributes.all { !it.isStandard })
        assertTrue(query.groupTraceByOtherAttributes.all { !it.isClassifier })
        assertEquals("branch", query.groupTraceByOtherAttributes.elementAt(0).name)
        assertEquals(Scope.Trace, query.groupTraceByOtherAttributes.elementAt(0).effectiveScope)
    }

    @Test
    fun groupByMeaninglessScopeTest() {
        val query = Query("group trace by l:name")
        assertTrue(query.isImplicitSelectAll)
        assertTrue(query.selectAll)
        assertFalse(query.isImplicitGroupBy)
        assertEquals(0, query.groupTraceByStandardAttributes.size)
        assertEquals(0, query.groupTraceByOtherAttributes.size)

        assertNotNull(query.warning)
        assertTrue("group" in query.warning!!.message!!)
    }

    @Test
    fun groupByImplicitFromSelectTest() {
        val query = Query("select avg(e:total), min(e:timestamp), max(e:timestamp)")
        assertFalse(query.isImplicitSelectAll)
        assertFalse(query.selectAll)
        assertFalse(query.selectAllLog)
        assertFalse(query.selectAllTrace)
        assertFalse(query.selectAllEvent)
        assertTrue(query.isImplicitGroupBy)
        assertFalse(query.isGroupLogBy)
        assertFalse(query.isGroupTraceBy)
        assertFalse(query.isGroupEventBy)
        assertEquals(0, query.selectLogExpressions.size)
        assertEquals(0, query.selectTraceExpressions.size)
        assertEquals(3, query.selectEventExpressions.size)
        assertTrue(query.selectEventExpressions.all { !it.isTerminal })
        assertTrue(query.selectEventExpressions.all { it is Function && it.type == FunctionType.Aggregation })
        assertEquals(0, query.groupLogByStandardAttributes.size)
        assertEquals(0, query.groupTraceByStandardAttributes.size)
        assertEquals(0, query.groupEventByStandardAttributes.size)
        assertEquals(0, query.groupLogByOtherAttributes.size)
        assertEquals(0, query.groupTraceByOtherAttributes.size)
        assertEquals(0, query.groupEventByOtherAttributes.size)
    }

    @Test
    fun groupByImplitFromOrderByTest() {
        val query = Query("order by avg(e:total), min(e:timestamp), max(e:timestamp)")
        assertFalse(query.isImplicitSelectAll)
        assertFalse(query.selectAll)
        assertFalse(query.selectAllLog)
        assertFalse(query.selectAllTrace)
        assertFalse(query.selectAllEvent)
        assertTrue(query.isImplicitGroupBy)
        assertFalse(query.isGroupLogBy)
        assertFalse(query.isGroupTraceBy)
        assertFalse(query.isGroupEventBy)
        assertEquals(0, query.groupLogByStandardAttributes.size)
        assertEquals(0, query.groupTraceByStandardAttributes.size)
        assertEquals(0, query.groupEventByStandardAttributes.size)
        assertEquals(0, query.groupLogByOtherAttributes.size)
        assertEquals(0, query.groupTraceByOtherAttributes.size)
        assertEquals(0, query.groupEventByOtherAttributes.size)
        assertEquals(0, query.orderByLogExpressions.size)
        assertEquals(0, query.orderByTraceExpressions.size)
        assertEquals(0, query.orderByEventExpressions.size)

        // It is meaningless to order results here, as there is returned only one entity for each scope
        assertTrue(query.warning is IllegalArgumentException)
        assertTrue("implicit" in (query.warning as IllegalArgumentException).message!!)
    }

    @Test
    fun groupByImplicitWithHoistingTest() {
        val query = Query("select avg(^^e:total), min(^^e:timestamp), max(^^e:timestamp)")
        assertFalse(query.selectAll)
        assertEquals(3, query.selectLogExpressions.size)
        assertEquals(0, query.selectTraceExpressions.size)
        assertEquals(0, query.selectEventExpressions.size)
        assertTrue(query.selectLogExpressions.all { !it.isTerminal })
        assertTrue(query.selectLogExpressions.all { it is Function && it.type == FunctionType.Aggregation })
        assertEquals(0, query.groupLogByStandardAttributes.size)
        assertEquals(0, query.groupTraceByStandardAttributes.size)
        assertEquals(0, query.groupEventByStandardAttributes.size)
        assertEquals(0, query.groupLogByOtherAttributes.size)
        assertEquals(0, query.groupTraceByOtherAttributes.size)
        assertEquals(0, query.groupEventByOtherAttributes.size)
    }

    @Test
    fun groupByWarningsTest() {
        val invalidScopes = listOf("group event by t:currency", "group trace by l:name")
        invalidScopes.forEach {
            val query = Query(it)
            assertNotNull(query.warning)
            assertTrue(query.warning!!.message!!.contains("group"))
        }
    }

    @Test
    fun orderBySimpleTest() {
        val query = Query("order by e:timestamp")
        assertEquals(0, query.orderByLogExpressions.size)
        assertEquals(0, query.orderByTraceExpressions.size)
        assertEquals(1, query.orderByEventExpressions.size)
        assertEquals(OrderDirection.Ascending, query.orderByEventExpressions[0].direction)
        assertTrue(query.orderByEventExpressions[0].base.let { it is Attribute && it.isStandard })
    }

    @Test
    fun orderByWithModifierAndScopesTest() {
        val query = Query("order by t:total desc, e:timestamp")
        assertEquals(0, query.orderByLogExpressions.size)
        assertEquals(1, query.orderByTraceExpressions.size)
        assertEquals(OrderDirection.Descending, query.orderByTraceExpressions[0].direction)
        assertTrue(query.orderByTraceExpressions[0].base.let { it is Attribute && it.isStandard })
        assertEquals(1, query.orderByEventExpressions.size)
        assertEquals(OrderDirection.Ascending, query.orderByEventExpressions[0].direction)
        assertTrue(query.orderByEventExpressions[0].base.let { it is Attribute && it.isStandard })
    }

    @Test
    fun orderByWithModifierAndScopes2Test() {
        val query = Query("order by e:timestamp, t:total desc")
        assertEquals(0, query.orderByLogExpressions.size)
        assertEquals(1, query.orderByTraceExpressions.size)
        assertEquals(OrderDirection.Descending, query.orderByTraceExpressions[0].direction)
        assertTrue(query.orderByTraceExpressions[0].base.let { it is Attribute && it.isStandard })
        assertEquals(1, query.orderByEventExpressions.size)
        assertEquals(OrderDirection.Ascending, query.orderByEventExpressions[0].direction)
        assertTrue(query.orderByEventExpressions[0].base.let { it is Attribute && it.isStandard })
    }

    @Test
    fun orderByExpressionTest() {
        val query = Query("group trace by e:name order by min(^e:timestamp)")
        assertEquals(0, query.orderByLogExpressions.size)
        assertEquals(1, query.orderByTraceExpressions.size)
        assertEquals(OrderDirection.Ascending, query.orderByTraceExpressions[0].direction)
        assertTrue(query.orderByTraceExpressions[0].base.let {
            it is Function
                    && it.type == FunctionType.Aggregation
                    && it.effectiveScope == Scope.Trace
                    && it.children[0].scope == Scope.Event
        })
        assertEquals(0, query.orderByEventExpressions.size)
    }

    @Test
    fun orderByExpression2Test() {
        val query = Query(
            """group trace by e:name
                order by [l:basePrice] * avg(^e:total) * 3.141592 desc"""
        )
        assertEquals(0, query.orderByLogExpressions.size)
        assertEquals(1, query.orderByTraceExpressions.size)
        assertEquals(OrderDirection.Descending, query.orderByTraceExpressions[0].direction)
        assertEquals(
            "[log:basePrice]*avg(^event:cost:total)*3.141592 desc",
            query.orderByTraceExpressions[0].toString()
        )
        val expression = query.orderByTraceExpressions[0].base
        assertEquals(Scope.Trace, expression.effectiveScope)
        assertEquals("[log:basePrice]*avg(^event:cost:total)*3.141592", expression.toString())
        assertEquals(0, query.orderByEventExpressions.size)
    }

    @Test
    fun limitSingleTest() {
        val query = Query("limit l:1")
        assertEquals(1, query.logLimit)
        assertEquals(-1, query.traceLimit)
        assertEquals(-1, query.eventLimit)
        assertEquals(-1, query.logOffset)
        assertEquals(-1, query.traceOffset)
        assertEquals(-1, query.eventOffset)
    }

    @Test
    fun limitAllTest() {
        val query = Query("limit e:3, t:2, l:1")
        assertEquals(1, query.logLimit)
        assertEquals(2, query.traceLimit)
        assertEquals(3, query.eventLimit)
        assertEquals(-1, query.logOffset)
        assertEquals(-1, query.traceOffset)
        assertEquals(-1, query.eventOffset)
    }

    @Test
    fun limitDuplicatesTest() {
        val query = Query("limit e:3, t:2, l:1, e:10")
        assertEquals(1, query.logLimit)
        assertEquals(2, query.traceLimit)
        assertEquals(10, query.eventLimit)
        assertEquals(-1, query.logOffset)
        assertEquals(-1, query.traceOffset)
        assertEquals(-1, query.eventOffset)

        assertNotNull(query.warning)
        assertTrue("duplicate" in query.warning!!.message!!)
    }

    @Test
    fun limitRealNumberTest() {
        val query = Query("limit e:3.14, t:2.72, l:1")
        assertEquals(1, query.logLimit)
        assertEquals(3, query.traceLimit)
        assertEquals(3, query.eventLimit)
        assertEquals(-1, query.logOffset)
        assertEquals(-1, query.traceOffset)
        assertEquals(-1, query.eventOffset)

        assertNotNull(query.warning)
        assertTrue("decimal" in query.warning!!.message!!)
    }

    @Test
    fun offsetSingleTest() {
        val query = Query("offset l:1")
        assertEquals(-1, query.logLimit)
        assertEquals(-1, query.traceLimit)
        assertEquals(-1, query.eventLimit)
        assertEquals(1, query.logOffset)
        assertEquals(-1, query.traceOffset)
        assertEquals(-1, query.eventOffset)
    }

    @Test
    fun offsetAllTest() {
        val query = Query("offset e:3, t:2, l:1")
        assertEquals(-1, query.logLimit)
        assertEquals(-1, query.traceLimit)
        assertEquals(-1, query.eventLimit)
        assertEquals(1, query.logOffset)
        assertEquals(2, query.traceOffset)
        assertEquals(3, query.eventOffset)
    }

    @Test
    fun offsetDuplicatesTest() {
        val query = Query("offset e:3, t:2, l:1, e:10")
        assertEquals(-1, query.logLimit)
        assertEquals(-1, query.traceLimit)
        assertEquals(-1, query.eventLimit)
        assertEquals(1, query.logOffset)
        assertEquals(2, query.traceOffset)
        assertEquals(10, query.eventOffset)

        assertNotNull(query.warning)
        assertTrue("duplicate" in query.warning!!.message!!)
    }

    @Test
    fun offsetRealNumberTest() {
        val query = Query("offset e:3.14, t:2.72, l:1")
        assertEquals(-1, query.logLimit)
        assertEquals(-1, query.traceLimit)
        assertEquals(-1, query.eventLimit)
        assertEquals(1, query.logOffset)
        assertEquals(3, query.traceOffset)
        assertEquals(3, query.eventOffset)

        assertNotNull(query.warning)
        assertTrue("decimal" in query.warning!!.message!!)
    }

    @Test
    fun commentLineTest() {
        val query = Query(
            """select e:name
            --where e:timestamp > D2020-01-01
            order by e:timestamp
            """
        )
        assertEquals(1, query.selectEventStandardAttributes.size)
        assertEquals(Expression.empty, query.whereExpression)
        assertEquals(1, query.orderByEventExpressions.size)
    }

    @Test
    fun commentLine2Test() {
        val query = Query(
            """select e:name
            //where e:timestamp > D2020-01-01
            order by e:timestamp
            """
        )
        assertEquals(1, query.selectEventStandardAttributes.size)
        assertEquals(Expression.empty, query.whereExpression)
        assertEquals(1, query.orderByEventExpressions.size)
    }

    @Test
    fun commentBlockTest() {
        val query = Query(
            """select e:name
            /*where e:timestamp > D2020-01-01
            group by e:name
            */
            order by e:timestamp
            """
        )
        assertEquals(1, query.selectEventStandardAttributes.size)
        assertEquals(Expression.empty, query.whereExpression)
        assertFalse(query.isImplicitGroupBy)
        assertFalse(query.isGroupLogBy)
        assertFalse(query.isGroupTraceBy)
        assertFalse(query.isGroupEventBy)
        assertEquals(1, query.orderByEventExpressions.size)
    }

}