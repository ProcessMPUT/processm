package processm.core.querylanguage

import org.antlr.v4.runtime.RecognitionException
import org.junit.jupiter.api.Tag
import processm.core.log.attribute.Attribute.Companion.CONCEPT_NAME
import processm.core.log.attribute.Attribute.Companion.COST_CURRENCY
import processm.core.log.attribute.Attribute.Companion.COST_TOTAL
import processm.core.log.attribute.Attribute.Companion.TIME_TIMESTAMP
import kotlin.test.*

@Tag("PQL")
@Suppress("MapGetWithNotNullAssertionOperator")
class QueryTests {
    @Test
    fun basicSelectTest() {
        val query = Query("select l:name, t:name, t:currency, e:name, e:total")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(1, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        assertEquals(CONCEPT_NAME, query.selectStandardAttributes[Scope.Log]!!.elementAt(0).standardName)
        assertTrue(query.selectStandardAttributes[Scope.Log]!!.all { it.isStandard })
        assertTrue(query.selectStandardAttributes[Scope.Log]!!.all { it.effectiveScope == Scope.Log })
        assertTrue(query.selectStandardAttributes[Scope.Log]!!.all { !it.isClassifier })
        // trace scope
        assertEquals(2, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        assertEquals(CONCEPT_NAME, query.selectStandardAttributes[Scope.Trace]!!.elementAt(0).standardName)
        assertEquals(COST_CURRENCY, query.selectStandardAttributes[Scope.Trace]!!.elementAt(1).standardName)
        assertTrue(query.selectStandardAttributes[Scope.Trace]!!.all { it.isStandard })
        assertTrue(query.selectStandardAttributes[Scope.Trace]!!.all { it.effectiveScope == Scope.Trace })
        assertTrue(query.selectStandardAttributes[Scope.Trace]!!.all { !it.isClassifier })
        // event scope
        assertEquals(2, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Event]!!.size)
        assertEquals(CONCEPT_NAME, query.selectStandardAttributes[Scope.Event]!!.elementAt(0).standardName)
        assertEquals(COST_TOTAL, query.selectStandardAttributes[Scope.Event]!!.elementAt(1).standardName)
        assertTrue(query.selectStandardAttributes[Scope.Event]!!.all { it.isStandard })
        assertTrue(query.selectStandardAttributes[Scope.Event]!!.all { it.effectiveScope == Scope.Event })
        assertTrue(query.selectStandardAttributes[Scope.Event]!!.all { !it.isClassifier })
    }

    @Test
    fun scopedSelectAllTest() {
        val query = Query("select t:name, e:*, t:total, e:concept:name")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertTrue(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(2, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        assertEquals(CONCEPT_NAME, query.selectStandardAttributes[Scope.Trace]!!.elementAt(0).standardName)
        assertEquals(COST_TOTAL, query.selectStandardAttributes[Scope.Trace]!!.elementAt(1).standardName)
        assertTrue(query.selectStandardAttributes[Scope.Trace]!!.all { it.isStandard })
        assertTrue(query.selectStandardAttributes[Scope.Trace]!!.all { it.effectiveScope == Scope.Trace })
        assertTrue(query.selectStandardAttributes[Scope.Trace]!!.all { !it.isClassifier })
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Event]!!.size)

        assertNotNull(query.warning)
        assertTrue("select all" in query.warning!!.message!!)
    }

    @Test
    fun scopedSelectAll2Test() {
        val query = Query("select t:*, e:*, l:*")
        assertTrue(query.selectAll[Scope.Log]!!)
        assertTrue(query.selectAll[Scope.Trace]!!)
        assertTrue(query.selectAll[Scope.Event]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
    }

    @Test
    fun selectUsingClassifierTest() {
        val query = Query("select t:c:businesscase, e:classifier:activity_resource")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(1, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        assertEquals("classifier:businesscase", query.selectStandardAttributes[Scope.Trace]!!.elementAt(0).standardName)
        assertTrue(query.selectStandardAttributes[Scope.Trace]!!.all { it.isStandard })
        assertTrue(query.selectStandardAttributes[Scope.Trace]!!.all { it.effectiveScope == Scope.Trace })
        assertTrue(query.selectStandardAttributes[Scope.Trace]!!.all { it.isClassifier })
        // event scope
        assertEquals(1, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Event]!!.size)
        assertEquals(
            "classifier:activity_resource",
            query.selectStandardAttributes[Scope.Event]!!.elementAt(0).standardName
        )
        assertTrue(query.selectStandardAttributes[Scope.Event]!!.all { it.isStandard })
        assertTrue(query.selectStandardAttributes[Scope.Event]!!.all { it.effectiveScope == Scope.Event })
        assertTrue(query.selectStandardAttributes[Scope.Event]!!.all { it.isClassifier })
    }

    @Test
    fun selectUsingNonStandardClassifierTest() {
        val query = Query("select [t:classifier:bu$1n3\$\$c4\$3], [e:classifier:concept:name+lifecycle:transition]")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(1, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        assertEquals("classifier:bu\$1n3\$\$c4\$3", query.selectOtherAttributes[Scope.Trace]!!.elementAt(0).name)
        assertTrue(query.selectOtherAttributes[Scope.Trace]!!.none { it.isStandard })
        assertTrue(query.selectOtherAttributes[Scope.Trace]!!.all { it.effectiveScope == Scope.Trace })
        assertTrue(query.selectOtherAttributes[Scope.Trace]!!.all { it.isClassifier })
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(1, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Event]!!.size)
        assertEquals(
            "classifier:concept:name+lifecycle:transition",
            query.selectOtherAttributes[Scope.Event]!!.elementAt(0).name
        )
        assertTrue(query.selectOtherAttributes[Scope.Event]!!.none { it.isStandard })
        assertTrue(query.selectOtherAttributes[Scope.Event]!!.all { it.effectiveScope == Scope.Event })
        assertTrue(query.selectOtherAttributes[Scope.Event]!!.all { it.isClassifier })
    }

    @Test
    fun selectAggregationTest() {
        val query = Query("select min(t:total), avg(t:total), max(t:total)")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(3, query.selectExpressions[Scope.Trace]!!.size)
        assertEquals("min(trace:cost:total)", query.selectExpressions[Scope.Trace]!!.elementAt(0).toString())
        assertEquals("avg(trace:cost:total)", query.selectExpressions[Scope.Trace]!!.elementAt(1).toString())
        assertEquals("max(trace:cost:total)", query.selectExpressions[Scope.Trace]!!.elementAt(2).toString())
        assertTrue(query.selectExpressions[Scope.Trace]!!.all { !it.isTerminal })
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Event]!!.size)
    }

    @Test
    fun selectNonStandardAttributesTest() {
        val query = Query("select [e:conceptowy:name], [e:time:timestamp], [org:resource2]")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(3, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Event]!!.size)
        assertEquals("conceptowy:name", query.selectOtherAttributes[Scope.Event]!!.elementAt(0).name)
        assertEquals("[event:conceptowy:name]", query.selectOtherAttributes[Scope.Event]!!.elementAt(0).toString())
        assertEquals(TIME_TIMESTAMP, query.selectOtherAttributes[Scope.Event]!!.elementAt(1).name)
        assertEquals("[event:time:timestamp]", query.selectOtherAttributes[Scope.Event]!!.elementAt(1).toString())
        assertEquals("org:resource2", query.selectOtherAttributes[Scope.Event]!!.elementAt(2).name)
        assertEquals("[event:org:resource2]", query.selectOtherAttributes[Scope.Event]!!.elementAt(2).toString())
        assertTrue(query.selectOtherAttributes[Scope.Event]!!.all { !it.isStandard })
        assertTrue(query.selectOtherAttributes[Scope.Event]!!.all { !it.isClassifier })
        assertTrue(query.selectOtherAttributes[Scope.Event]!!.all { it.isTerminal })
        assertTrue(query.selectOtherAttributes[Scope.Event]!!.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectExpressionTest() {
        val query = Query(
            "select [e:conceptowy:name] + e:resource, max(timestamp) - \t \n min(timestamp)" +
                    "group by [e:conceptowy:name], e:resource"
        )
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(2, query.selectExpressions[Scope.Event]!!.size)
        assertEquals(
            "[event:conceptowy:name] + event:org:resource",
            query.selectExpressions[Scope.Event]!!.elementAt(0).toString()
        )
        assertEquals(
            "max(event:time:timestamp) - min(event:time:timestamp)",
            query.selectExpressions[Scope.Event]!!.elementAt(1).toString()
        )
        assertTrue(query.selectExpressions[Scope.Event]!!.all { !it.isTerminal })
    }

    @Test
    fun selectAllImplicitTest() {
        val query = Query("")
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Trace]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Event]!!)
        assertTrue(query.selectAll[Scope.Log]!!)
        assertTrue(query.selectAll[Scope.Trace]!!)
        assertTrue(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Event]!!.size)
    }

    @Test
    fun selectConstantsTest() {
        val query = Query("select l:1, l:2 + t:3, l:4 * t:5 + e:6, 7 / 8 - 9, 10 * null, t:null/11, l:D2020-03-12")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(2, query.selectExpressions[Scope.Log]!!.size)
        assertEquals("log:1.0", query.selectExpressions[Scope.Log]!!.elementAt(0).toString())
        assertEquals("log:D2020-03-12T00:00:00Z", query.selectExpressions[Scope.Log]!!.elementAt(1).toString())
        assertTrue(query.selectExpressions[Scope.Log]!!.all { it.isTerminal })
        assertTrue(query.selectExpressions[Scope.Log]!!.all { it.effectiveScope == Scope.Log })
        // trace scope
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(2, query.selectExpressions[Scope.Trace]!!.size)
        assertEquals("log:2.0 + trace:3.0", query.selectExpressions[Scope.Trace]!!.elementAt(0).toString())
        assertEquals("trace:null / 11.0", query.selectExpressions[Scope.Trace]!!.elementAt(1).toString())
        assertTrue(query.selectExpressions[Scope.Trace]!!.all { !it.isTerminal })
        assertTrue(query.selectExpressions[Scope.Trace]!!.all { it.effectiveScope == Scope.Trace })
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(3, query.selectExpressions[Scope.Event]!!.size)
        assertEquals("log:4.0 * trace:5.0 + event:6.0", query.selectExpressions[Scope.Event]!!.elementAt(0).toString())
        assertEquals("7.0 / 8.0 - 9.0", query.selectExpressions[Scope.Event]!!.elementAt(1).toString())
        assertEquals("10.0 * null", query.selectExpressions[Scope.Event]!!.elementAt(2).toString())
        assertTrue(query.selectExpressions[Scope.Event]!!.all { !it.isTerminal })
        assertTrue(query.selectExpressions[Scope.Event]!!.all { it.effectiveScope == Scope.Event })
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
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(17, query.selectExpressions[Scope.Event]!!.size)
        assertEquals("D2020-03-13T00:00:00Z", query.selectExpressions[Scope.Event]!!.elementAt(0).toString())
        assertEquals("D2020-03-13T16:45:00Z", query.selectExpressions[Scope.Event]!!.elementAt(1).toString())
        assertEquals("D2020-03-13T16:45:50Z", query.selectExpressions[Scope.Event]!!.elementAt(2).toString())
        assertEquals("D2020-03-13T16:45:50.333Z", query.selectExpressions[Scope.Event]!!.elementAt(3).toString())
        assertEquals("D2020-03-13T14:45:00Z", query.selectExpressions[Scope.Event]!!.elementAt(4).toString())
        assertEquals("D2020-03-13T14:45:00Z", query.selectExpressions[Scope.Event]!!.elementAt(5).toString())
        assertEquals("D2020-03-13T16:45:00Z", query.selectExpressions[Scope.Event]!!.elementAt(6).toString())
        assertEquals("D2020-03-13T00:00:00Z", query.selectExpressions[Scope.Event]!!.elementAt(7).toString())
        assertEquals("D2020-03-13T16:45:00Z", query.selectExpressions[Scope.Event]!!.elementAt(8).toString())
        assertEquals("D2020-03-13T16:45:50Z", query.selectExpressions[Scope.Event]!!.elementAt(9).toString())
        assertEquals("D2020-03-13T16:45:50.333Z", query.selectExpressions[Scope.Event]!!.elementAt(10).toString())
        assertEquals("D2020-03-13T14:45:00Z", query.selectExpressions[Scope.Event]!!.elementAt(11).toString())
        assertEquals("D2020-03-13T16:45:00Z", query.selectExpressions[Scope.Event]!!.elementAt(12).toString())
        assertEquals("D2020-03-13T16:45:50Z", query.selectExpressions[Scope.Event]!!.elementAt(13).toString())
        assertEquals("D2020-03-13T16:45:50.333Z", query.selectExpressions[Scope.Event]!!.elementAt(14).toString())
        assertEquals("D2020-03-13T14:45:00Z", query.selectExpressions[Scope.Event]!!.elementAt(15).toString())
        assertEquals("D2020-03-13T16:45:00Z", query.selectExpressions[Scope.Event]!!.elementAt(16).toString())
        assertTrue(query.selectExpressions[Scope.Event]!!.all { it.isTerminal })
        assertTrue(query.selectExpressions[Scope.Event]!!.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectIEEE754Test() {
        val query = Query(
            "select 0, 0.0, 0.00, -0, -0.0, 1, 1.0, -1, -1.0, ${Math.PI}, ${Double.MIN_VALUE}, ${Double.MAX_VALUE}"
        )
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(12, query.selectExpressions[Scope.Event]!!.size)
        assertEquals("0.0", query.selectExpressions[Scope.Event]!!.elementAt(0).toString())
        assertEquals("0.0", query.selectExpressions[Scope.Event]!!.elementAt(1).toString())
        assertEquals("0.0", query.selectExpressions[Scope.Event]!!.elementAt(2).toString())
        assertEquals("-0.0", query.selectExpressions[Scope.Event]!!.elementAt(3).toString())
        assertEquals("-0.0", query.selectExpressions[Scope.Event]!!.elementAt(4).toString())
        assertEquals("1.0", query.selectExpressions[Scope.Event]!!.elementAt(5).toString())
        assertEquals("1.0", query.selectExpressions[Scope.Event]!!.elementAt(6).toString())
        assertEquals("-1.0", query.selectExpressions[Scope.Event]!!.elementAt(7).toString())
        assertEquals("-1.0", query.selectExpressions[Scope.Event]!!.elementAt(8).toString())
        assertEquals(Math.PI.toString(), query.selectExpressions[Scope.Event]!!.elementAt(9).toString())
        assertEquals(Double.MIN_VALUE.toString(), query.selectExpressions[Scope.Event]!!.elementAt(10).toString())
        assertEquals(Double.MAX_VALUE.toString(), query.selectExpressions[Scope.Event]!!.elementAt(11).toString())
        assertTrue(query.selectExpressions[Scope.Event]!!.all { it.isTerminal })
        assertTrue(query.selectExpressions[Scope.Event]!!.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectBooleanTest() {
        val query = Query("select true, false")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(2, query.selectExpressions[Scope.Event]!!.size)
        assertEquals("true", query.selectExpressions[Scope.Event]!!.elementAt(0).toString())
        assertEquals("false", query.selectExpressions[Scope.Event]!!.elementAt(1).toString())
        assertTrue(query.selectExpressions[Scope.Event]!!.all { it.isTerminal })
        assertTrue(query.selectExpressions[Scope.Event]!!.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectStringTest() {
        val query = Query("select 'single-quoted', \"double-quoted\"")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(2, query.selectExpressions[Scope.Event]!!.size)
        assertEquals("single-quoted", query.selectExpressions[Scope.Event]!!.elementAt(0).toString())
        assertEquals("double-quoted", query.selectExpressions[Scope.Event]!!.elementAt(1).toString())
        assertTrue(query.selectExpressions[Scope.Event]!!.all { it.isTerminal })
        assertTrue(query.selectExpressions[Scope.Event]!!.all { it.effectiveScope == Scope.Event })
    }

    @Test
    fun selectNowTest() {
        val query = Query("select now()")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        // log scope
        assertEquals(0, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        // trace scope
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        // event scope
        assertEquals(0, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectOtherAttributes[Scope.Event]!!.size)
        assertEquals(1, query.selectExpressions[Scope.Event]!!.size)
        assertEquals("now()", query.selectExpressions[Scope.Event]!![0].toString())

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
                "where ^^name",
                "group by",
                "order by [] limit l:1",
                "limit e:timestamp < D2020-01-01",
                "offset e:timestamp >= D2020-01-01",
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
            "select e:name, sum(e:total) where l:name='JournalReview' group by ^^e:name", // implicit group by at event scope + ungrouped attribute
            "select e:total + 10 group by e:name",
            "select avg(e:total), e:resource",
            "where avg(e:total) > 100",
            "select *, avg(e:total)",
            "select ^e:42",
            "where ^t:c:myclassifier not in ('a', 'b')",
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
    fun invalidUseOfClassifiersTest() {
        val invalidHoisting = listOf(
            "group by [^^c:Event Name]",
            "group by ^t:c:name",
            "select [l:c:main]",
            "where [e:classifier:concept:name+lifecycle:transition] in ('acceptcomplete', 'rejectcomplete')"
        )

        invalidHoisting.forEach {
            assertFailsWith<IllegalArgumentException>(it) { Query(it) }.apply {
                assertNotNull(message)
                assertTrue("classifier" in message!!)
            }
        }
    }

    @Test
    fun whereSimpleTest() {
        val query = Query("where dayofweek(e:timestamp) in (1, 7)")
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Trace]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Event]!!)
        assertEquals("dayofweek(event:time:timestamp) in (1.0,7.0)", query.whereExpression.toString())
        assertEquals(Scope.Event, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereSimpleWithHoistingTest() {
        val query = Query("where dayofweek(^e:timestamp) in (1, 7)")
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Trace]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Event]!!)
        assertEquals("dayofweek(^event:time:timestamp) in (1.0,7.0)", query.whereExpression.toString())
        assertEquals(Scope.Trace, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereSimpleWithHoistingTest2() {
        val query = Query("where dayofweek(^^e:timestamp) in (1, 7)")
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Trace]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Event]!!)
        assertEquals("dayofweek(^^event:time:timestamp) in (1.0,7.0)", query.whereExpression.toString())
        assertEquals(Scope.Log, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLogicExprWithHoistingTest() {
        val query = Query("where not(t:currency = ^e:currency)")
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Trace]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Event]!!)
        assertEquals("not (trace:cost:currency = ^event:cost:currency)", query.whereExpression.toString())
        assertEquals(Scope.Trace, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLogicExprTest() {
        val query = Query("where t:currency != e:currency")
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Trace]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Event]!!)
        assertEquals("trace:cost:currency != event:cost:currency", query.whereExpression.toString())
        assertEquals(Scope.Event, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLogicExpr2Test() {
        val query = Query("where not(t:currency = ^e:currency) and t:total is null")
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Trace]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Event]!!)
        assertEquals(
            "not (trace:cost:currency = ^event:cost:currency) and trace:cost:total is null",
            query.whereExpression.toString()
        )
        assertEquals(Scope.Trace, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLogicExpr3Test() {
        val query = Query("where (not(t:currency = ^e:currency) or ^e:timestamp >= D2020-01-01) and t:total is null")
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Trace]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Event]!!)
        assertEquals(
            "(not (trace:cost:currency = ^event:cost:currency) or ^event:time:timestamp >= D2020-01-01T00:00:00Z) and trace:cost:total is null",
            query.whereExpression.toString()
        )
        assertEquals(Scope.Trace, query.whereExpression.effectiveScope)
    }

    @Test
    fun whereLikeAndMatchesTest() {
        val query = Query("where t:name like 'transaction %' and ^e:resource matches '^[A-Z][a-z]+ [A-Z][a-z]+$'")
        assertEquals(
            "trace:concept:name like transaction % and ^event:org:resource matches ^[A-Z][a-z]+ [A-Z][a-z]+\$",
            query.whereExpression.toString()
        )

        assertNull(query.warning)
    }

    @Test
    fun groupScopeByClassifierTest() {
        val query = Query("group by ^e:classifier:activity")
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Log]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Trace]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Event]!!)
        assertFalse(query.isGroupBy[Scope.Log]!!)
        assertTrue(query.isGroupBy[Scope.Trace]!!)
        assertFalse(query.isGroupBy[Scope.Event]!!)
        assertEquals(0, query.groupByStandardAttributes[Scope.Log]!!.size)
        assertEquals(1, query.groupByStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Event]!!.size)
        assertTrue(query.groupByStandardAttributes[Scope.Trace]!!.all { it.isStandard })
        assertTrue(query.groupByStandardAttributes[Scope.Trace]!!.all { it.isClassifier })
        assertEquals("classifier:activity", query.groupByStandardAttributes[Scope.Trace]!!.elementAt(0).standardName)
        assertEquals(Scope.Trace, query.groupByStandardAttributes[Scope.Trace]!!.elementAt(0).effectiveScope)
    }

    @Test
    fun groupEventByStandardAttributeTest() {
        val query = Query(
            """select t:name, e:name, sum(e:total)
            group by e:name"""
        )
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Log]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Trace]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Event]!!)
        assertFalse(query.isGroupBy[Scope.Log]!!)
        assertFalse(query.isGroupBy[Scope.Trace]!!)
        assertTrue(query.isGroupBy[Scope.Event]!!)
        assertEquals(CONCEPT_NAME, query.selectStandardAttributes[Scope.Trace]!!.elementAt(0).standardName)
        assertEquals(CONCEPT_NAME, query.selectStandardAttributes[Scope.Event]!!.elementAt(0).standardName)
        assertEquals("sum(event:cost:total)", query.selectExpressions[Scope.Event]!!.elementAt(0).toString())
        assertEquals(0, query.groupByStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Trace]!!.size)
        assertEquals(1, query.groupByStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Event]!!.size)
        assertTrue(query.groupByStandardAttributes[Scope.Event]!!.all { it.isStandard })
        assertTrue(query.groupByStandardAttributes[Scope.Event]!!.all { !it.isClassifier })
        assertEquals(CONCEPT_NAME, query.groupByStandardAttributes[Scope.Event]!!.elementAt(0).standardName)
        assertEquals(Scope.Event, query.groupByStandardAttributes[Scope.Event]!!.elementAt(0).effectiveScope)
    }

    @Test
    fun groupTraceByEventStandardAttributeTest() {
        val query = Query(
            """select e:name, sum(e:total)
            group by ^e:name, e:name"""
        )
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Log]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Trace]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Event]!!)
        assertFalse(query.isGroupBy[Scope.Log]!!)
        assertTrue(query.isGroupBy[Scope.Trace]!!)
        assertTrue(query.isGroupBy[Scope.Event]!!)
        assertEquals(CONCEPT_NAME, query.selectStandardAttributes[Scope.Event]!!.elementAt(0).standardName)
        assertEquals("sum(event:cost:total)", query.selectExpressions[Scope.Event]!!.elementAt(0).toString())
        assertEquals(0, query.groupByStandardAttributes[Scope.Log]!!.size)
        assertEquals(1, query.groupByStandardAttributes[Scope.Trace]!!.size)
        assertEquals(1, query.groupByStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Event]!!.size)
        assertTrue(query.groupByStandardAttributes[Scope.Trace]!!.all { it.isStandard })
        assertTrue(query.groupByStandardAttributes[Scope.Trace]!!.all { !it.isClassifier })
        assertEquals(CONCEPT_NAME, query.groupByStandardAttributes[Scope.Trace]!!.elementAt(0).standardName)
        assertEquals(CONCEPT_NAME, query.groupByStandardAttributes[Scope.Event]!!.elementAt(0).standardName)
        assertEquals(Scope.Event, query.groupByStandardAttributes[Scope.Trace]!!.elementAt(0).scope)
        assertEquals(Scope.Trace, query.groupByStandardAttributes[Scope.Trace]!!.elementAt(0).effectiveScope)
    }

    @Test
    fun groupLogByEventStandardAttributeTest() {
        val query = Query(
            """select sum(e:total)
            group by ^^e:name"""
        )
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Log]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Trace]!!)
        assertTrue(query.isImplicitGroupBy[Scope.Event]!!)
        assertTrue(query.isGroupBy[Scope.Log]!!)
        assertFalse(query.isGroupBy[Scope.Trace]!!)
        assertFalse(query.isGroupBy[Scope.Event]!!)
        assertEquals("sum(event:cost:total)", query.selectExpressions[Scope.Event]!!.elementAt(0).toString())
        assertEquals(1, query.groupByStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Event]!!.size)
        assertTrue(query.groupByStandardAttributes[Scope.Log]!!.all { it.isStandard })
        assertTrue(query.groupByStandardAttributes[Scope.Log]!!.all { !it.isClassifier })
        assertEquals(CONCEPT_NAME, query.groupByStandardAttributes[Scope.Log]!!.elementAt(0).standardName)
        assertEquals(Scope.Event, query.groupByStandardAttributes[Scope.Log]!!.elementAt(0).scope)
        assertEquals(Scope.Log, query.groupByStandardAttributes[Scope.Log]!!.elementAt(0).effectiveScope)
    }

    @Test
    fun groupByImplicitScopeTest() {
        val query = Query("group by c:main, [t:branch]")
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertTrue(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        assertEquals(1, query.selectOtherAttributes[Scope.Trace]!!.size)
        assertEquals("[trace:branch]", query.selectOtherAttributes[Scope.Trace]!!.first().toString())
        assertEquals(1, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals("event:classifier:main", query.selectStandardAttributes[Scope.Event]!!.first().toString())
        assertFalse(query.isImplicitGroupBy[Scope.Log]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Trace]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Event]!!)
        assertFalse(query.isGroupBy[Scope.Log]!!)
        assertTrue(query.isGroupBy[Scope.Trace]!!)
        assertTrue(query.isGroupBy[Scope.Event]!!)
        assertEquals(0, query.groupByStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Trace]!!.size)
        assertEquals(1, query.groupByStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Log]!!.size)
        assertEquals(1, query.groupByOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Event]!!.size)
        assertTrue(query.groupByStandardAttributes[Scope.Trace]!!.all { it.isStandard })
        assertTrue(query.groupByStandardAttributes[Scope.Trace]!!.all { it.isClassifier })
        assertEquals("classifier:main", query.groupByStandardAttributes[Scope.Event]!!.elementAt(0).standardName)
        assertEquals(Scope.Event, query.groupByStandardAttributes[Scope.Event]!!.elementAt(0).effectiveScope)
        assertTrue(query.groupByOtherAttributes[Scope.Trace]!!.all { !it.isStandard })
        assertTrue(query.groupByOtherAttributes[Scope.Trace]!!.all { !it.isClassifier })
        assertEquals("branch", query.groupByOtherAttributes[Scope.Trace]!!.elementAt(0).name)
        assertEquals(Scope.Trace, query.groupByOtherAttributes[Scope.Trace]!!.elementAt(0).effectiveScope)
    }

    @Test
    fun groupByImplicitFromSelectTest() {
        val query = Query("select avg(e:total), min(e:timestamp), max(e:timestamp)")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Log]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Trace]!!)
        assertTrue(query.isImplicitGroupBy[Scope.Event]!!)
        assertFalse(query.isGroupBy[Scope.Log]!!)
        assertFalse(query.isGroupBy[Scope.Trace]!!)
        assertFalse(query.isGroupBy[Scope.Event]!!)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        assertEquals(3, query.selectExpressions[Scope.Event]!!.size)
        assertTrue(query.selectExpressions[Scope.Event]!!.all { !it.isTerminal })
        assertTrue(query.selectExpressions[Scope.Event]!!.all { it is Function && it.functionType == FunctionType.Aggregation })
        assertEquals(0, query.groupByStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Event]!!.size)
    }

    @Test
    fun groupByImplicitFromOrderByTest() {
        val query = Query("order by avg(e:total), min(e:timestamp), max(e:timestamp)")
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertTrue(query.selectAll[Scope.Log]!!)
        assertTrue(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Log]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Trace]!!)
        assertTrue(query.isImplicitGroupBy[Scope.Event]!!)
        assertFalse(query.isGroupBy[Scope.Log]!!)
        assertFalse(query.isGroupBy[Scope.Trace]!!)
        assertFalse(query.isGroupBy[Scope.Event]!!)
        assertEquals(0, query.groupByStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Event]!!.size)
        assertEquals(0, query.orderByExpressions[Scope.Log]!!.size)
        assertEquals(0, query.orderByExpressions[Scope.Trace]!!.size)
        assertEquals(0, query.orderByExpressions[Scope.Event]!!.size)

        // It is meaningless to order results here, as there is returned only one entity for each scope
        assertTrue(query.warning is IllegalArgumentException)
        assertTrue("implicit" in (query.warning as IllegalArgumentException).message!!)
    }

    @Test
    fun groupByImplicitMultiScopesTest() {
        val query = Query("select avg(t:total), min(e:timestamp), max(e:timestamp)")
        assertFalse(query.isImplicitSelectAll[Scope.Log]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Trace]!!)
        assertFalse(query.isImplicitSelectAll[Scope.Event]!!)
        assertFalse(query.selectAll[Scope.Log]!!)
        assertFalse(query.selectAll[Scope.Trace]!!)
        assertFalse(query.selectAll[Scope.Event]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Log]!!)
        assertTrue(query.isImplicitGroupBy[Scope.Trace]!!)
        assertTrue(query.isImplicitGroupBy[Scope.Event]!!)
        assertFalse(query.isGroupBy[Scope.Log]!!)
        assertFalse(query.isGroupBy[Scope.Trace]!!)
        assertFalse(query.isGroupBy[Scope.Event]!!)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        assertEquals(1, query.selectExpressions[Scope.Trace]!!.size)
        assertEquals(2, query.selectExpressions[Scope.Event]!!.size)
        assertTrue(query.selectExpressions[Scope.Event]!!.all { !it.isTerminal })
        assertTrue(query.selectExpressions[Scope.Event]!!.all { it is Function && it.functionType == FunctionType.Aggregation })
        assertEquals(0, query.groupByStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Event]!!.size)
    }

    @Test
    fun groupByImplicitWithHoistingTest() {
        val query = Query("select avg(^^e:total), min(^^e:timestamp), max(^^e:timestamp)")
        assertEquals(3, query.selectExpressions[Scope.Log]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Event]!!.size)
        assertTrue(query.selectExpressions[Scope.Log]!!.all { !it.isTerminal })
        assertTrue(query.selectExpressions[Scope.Log]!!.all { it is Function && it.functionType == FunctionType.Aggregation })
        assertEquals(0, query.groupByStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Event]!!.size)
    }

    /**
     * Demonstrates the bug from #105: PQL query does not group traces into variants.
     */
    @Test
    fun groupByWithHoistingAndOrderByCountTest() {
        val query = Query(
            "select l:name, count(t:name), e:name\n" +
                    "group by ^e:name\n" +
                    "order by count(t:name) desc\n" +
                    "limit l:1\n"
        )
        assertEquals(1, query.selectStandardAttributes[Scope.Log]!!.size)
        assertEquals(0, query.selectStandardAttributes[Scope.Trace]!!.size)
        assertEquals(1, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Log]!!.size)
        assertEquals(1, query.selectExpressions[Scope.Trace]!!.size)
        assertEquals(0, query.selectExpressions[Scope.Event]!!.size)
        assertTrue(query.selectExpressions[Scope.Trace]!!.all { !it.isTerminal })
        assertTrue(query.selectExpressions[Scope.Trace]!!.all { it is Function && it.functionType == FunctionType.Aggregation })
        assertEquals(0, query.groupByStandardAttributes[Scope.Log]!!.size)
        assertEquals(1, query.groupByStandardAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByStandardAttributes[Scope.Event]!!.size)
        query.groupByStandardAttributes[Scope.Trace]!!.all { it.isStandard }
        query.groupByStandardAttributes[Scope.Trace]!!.all { !it.isClassifier }
        query.groupByStandardAttributes[Scope.Trace]!!.all { it.effectiveScope == Scope.Trace }
        query.groupByStandardAttributes[Scope.Trace]!!.all { it.isTerminal }
        query.groupByStandardAttributes[Scope.Trace]!!.all { it.dropHoisting().effectiveScope == Scope.Event }
        assertEquals(0, query.groupByStandardAttributes[Scope.Event]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Log]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Trace]!!.size)
        assertEquals(0, query.groupByOtherAttributes[Scope.Event]!!.size)
    }

    @Test
    fun orderBySimpleTest() {
        val query = Query("order by e:timestamp")
        assertEquals(0, query.orderByExpressions[Scope.Log]!!.size)
        assertEquals(0, query.orderByExpressions[Scope.Trace]!!.size)
        assertEquals(1, query.orderByExpressions[Scope.Event]!!.size)
        assertEquals(OrderDirection.Ascending, query.orderByExpressions[Scope.Event]!![0].direction)
        assertTrue(query.orderByExpressions[Scope.Event]!![0].base.let { it is Attribute && it.isStandard })
    }

    @Test
    fun orderByWithModifierAndScopesTest() {
        val query = Query("order by t:total desc, e:timestamp")
        assertEquals(0, query.orderByExpressions[Scope.Log]!!.size)
        assertEquals(1, query.orderByExpressions[Scope.Trace]!!.size)
        assertEquals(OrderDirection.Descending, query.orderByExpressions[Scope.Trace]!![0].direction)
        assertTrue(query.orderByExpressions[Scope.Trace]!![0].base.let { it is Attribute && it.isStandard })
        assertEquals(1, query.orderByExpressions[Scope.Event]!!.size)
        assertEquals(OrderDirection.Ascending, query.orderByExpressions[Scope.Event]!![0].direction)
        assertTrue(query.orderByExpressions[Scope.Event]!![0].base.let { it is Attribute && it.isStandard })
    }

    @Test
    fun orderByWithModifierAndScopes2Test() {
        val query = Query("order by e:timestamp, t:total desc")
        assertEquals(0, query.orderByExpressions[Scope.Log]!!.size)
        assertEquals(1, query.orderByExpressions[Scope.Trace]!!.size)
        assertEquals(OrderDirection.Descending, query.orderByExpressions[Scope.Trace]!![0].direction)
        assertTrue(query.orderByExpressions[Scope.Trace]!![0].base.let { it is Attribute && it.isStandard })
        assertEquals(1, query.orderByExpressions[Scope.Event]!!.size)
        assertEquals(OrderDirection.Ascending, query.orderByExpressions[Scope.Event]!![0].direction)
        assertTrue(query.orderByExpressions[Scope.Event]!![0].base.let { it is Attribute && it.isStandard })
    }

    @Test
    fun orderByExpressionTest() {
        val query = Query("group by ^e:name order by min(^e:timestamp)")
        assertEquals(0, query.orderByExpressions[Scope.Log]!!.size)
        assertEquals(1, query.orderByExpressions[Scope.Trace]!!.size)
        assertEquals(OrderDirection.Ascending, query.orderByExpressions[Scope.Trace]!![0].direction)
        assertTrue(query.orderByExpressions[Scope.Trace]!![0].base.let {
            it is Function
                    && it.functionType == FunctionType.Aggregation
                    && it.effectiveScope == Scope.Trace
                    && it.children[0].scope == Scope.Event
        })
        assertEquals(0, query.orderByExpressions[Scope.Event]!!.size)
    }

    @Test
    fun orderByExpression2Test() {
        val query = Query(
            """group by ^e:name
            |order by [l:basePrice] * avg(^e:total) * 3.141592 desc""".trimMargin()
        )
        assertEquals(0, query.orderByExpressions[Scope.Log]!!.size)
        assertEquals(1, query.orderByExpressions[Scope.Trace]!!.size)
        assertEquals(OrderDirection.Descending, query.orderByExpressions[Scope.Trace]!![0].direction)
        assertEquals(
            "[log:basePrice] * avg(^event:cost:total) * 3.141592 desc",
            query.orderByExpressions[Scope.Trace]!![0].toString()
        )
        val expression = query.orderByExpressions[Scope.Trace]!![0].base
        assertEquals(Scope.Trace, expression.effectiveScope)
        assertEquals("[log:basePrice] * avg(^event:cost:total) * 3.141592", expression.toString())
        assertEquals(2, expression.line)
        assertEquals(40, expression.charPositionInLine)
        assertEquals(0, query.orderByExpressions[Scope.Event]!!.size)
    }

    @Test
    fun limitSingleTest() {
        val query = Query("limit l:1")
        assertEquals(1, query.limit[Scope.Log])
        assertEquals(null, query.limit[Scope.Trace])
        assertEquals(null, query.limit[Scope.Event])
        assertEquals(null, query.offset[Scope.Log])
        assertEquals(null, query.offset[Scope.Trace])
        assertEquals(null, query.offset[Scope.Event])
    }

    @Test
    fun limitAllTest() {
        val query = Query("limit e:3, t:2, l:1")
        assertEquals(1, query.limit[Scope.Log])
        assertEquals(2, query.limit[Scope.Trace])
        assertEquals(3, query.limit[Scope.Event])
        assertEquals(null, query.offset[Scope.Log])
        assertEquals(null, query.offset[Scope.Trace])
        assertEquals(null, query.offset[Scope.Event])
    }

    @Test
    fun limitDuplicatesTest() {
        val query = Query("limit e:3, t:2, l:1, e:10")
        assertEquals(1, query.limit[Scope.Log])
        assertEquals(2, query.limit[Scope.Trace])
        assertEquals(10, query.limit[Scope.Event])
        assertEquals(null, query.offset[Scope.Log])
        assertEquals(null, query.offset[Scope.Trace])
        assertEquals(null, query.offset[Scope.Event])

        assertNotNull(query.warning)
        assertTrue("duplicate" in query.warning!!.message!!)
    }

    @Test
    fun limitRealNumberTest() {
        val query = Query("limit e:3.14, t:2.72, l:1")
        assertEquals(1, query.limit[Scope.Log])
        assertEquals(3, query.limit[Scope.Trace])
        assertEquals(3, query.limit[Scope.Event])
        assertEquals(null, query.offset[Scope.Log])
        assertEquals(null, query.offset[Scope.Trace])
        assertEquals(null, query.offset[Scope.Event])

        assertNotNull(query.warning)
        assertTrue("decimal" in query.warning!!.message!!)
    }

    @Test
    fun offsetSingleTest() {
        val query = Query("offset l:1")
        assertEquals(null, query.limit[Scope.Log])
        assertEquals(null, query.limit[Scope.Trace])
        assertEquals(null, query.limit[Scope.Event])
        assertEquals(1, query.offset[Scope.Log])
        assertEquals(null, query.offset[Scope.Trace])
        assertEquals(null, query.offset[Scope.Event])
    }

    @Test
    fun offsetAllTest() {
        val query = Query("offset e:3, t:2, l:1")
        assertEquals(null, query.limit[Scope.Log])
        assertEquals(null, query.limit[Scope.Trace])
        assertEquals(null, query.limit[Scope.Event])
        assertEquals(1, query.offset[Scope.Log])
        assertEquals(2, query.offset[Scope.Trace])
        assertEquals(3, query.offset[Scope.Event])
    }

    @Test
    fun offsetDuplicatesTest() {
        val query = Query("offset e:3, t:2, l:1, e:10")
        assertEquals(null, query.limit[Scope.Log])
        assertEquals(null, query.limit[Scope.Trace])
        assertEquals(null, query.limit[Scope.Event])
        assertEquals(1, query.offset[Scope.Log])
        assertEquals(2, query.offset[Scope.Trace])
        assertEquals(10, query.offset[Scope.Event])

        assertNotNull(query.warning)
        assertTrue("duplicate" in query.warning!!.message!!)
    }

    @Test
    fun offsetRealNumberTest() {
        val query = Query("offset e:3.14, t:2.72, l:1")
        assertEquals(null, query.limit[Scope.Log])
        assertEquals(null, query.limit[Scope.Trace])
        assertEquals(null, query.limit[Scope.Event])
        assertEquals(1, query.offset[Scope.Log])
        assertEquals(3, query.offset[Scope.Trace])
        assertEquals(3, query.offset[Scope.Event])

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
        assertEquals(1, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(Expression.empty, query.whereExpression)
        assertEquals(1, query.orderByExpressions[Scope.Event]!!.size)
    }

    @Test
    fun commentLine2Test() {
        val query = Query(
            """select e:name
            //where e:timestamp > D2020-01-01
            order by e:timestamp
            """
        )
        assertEquals(1, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(Expression.empty, query.whereExpression)
        assertEquals(1, query.orderByExpressions[Scope.Event]!!.size)
    }

    @Test
    fun commentBlockTest() {
        val query = Query(
            """select e:name
            |/*where e:timestamp > D2020-01-01
            |group by e:name
            |*/
            |order by e:timestamp
            """.trimMargin()
        )
        assertEquals(1, query.selectStandardAttributes[Scope.Event]!!.size)
        assertEquals(Expression.empty, query.whereExpression)
        assertFalse(query.isImplicitGroupBy[Scope.Log]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Trace]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Event]!!)
        assertFalse(query.isGroupBy[Scope.Log]!!)
        assertFalse(query.isGroupBy[Scope.Trace]!!)
        assertFalse(query.isGroupBy[Scope.Event]!!)
        assertEquals(1, query.orderByExpressions[Scope.Event]!!.size)
        assertEquals(5, query.orderByExpressions[Scope.Event]!![0].line)
        assertEquals(9, query.orderByExpressions[Scope.Event]!![0].charPositionInLine)
    }

    @Test
    fun toStringTest() {
        val q = """select e:name
            |/*where e:timestamp > D2020-01-01
            |group by e:name
            |*/
            |order by e:timestamp
            """.trimMargin()
        val query = Query(q)
        assertEquals(q, query.toString())
    }

    @Test
    fun deleteAllLogsTest() {
        val query = Query("delete log")
        assertEquals(Scope.Log, query.deleteScope)
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Trace]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Event]!!)
        assertTrue(query.selectAll[Scope.Log]!!)
        assertTrue(query.selectAll[Scope.Trace]!!)
        assertTrue(query.selectAll[Scope.Event]!!)
        assertTrue(query.selectStandardAttributes.values.all { it.isEmpty() })
        assertTrue(query.selectOtherAttributes.values.all { it.isEmpty() })
        assertTrue(query.selectExpressions.values.all { it.isEmpty() })
        assertFalse(query.isGroupBy[Scope.Log]!!)
        assertFalse(query.isGroupBy[Scope.Trace]!!)
        assertFalse(query.isGroupBy[Scope.Event]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Log]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Trace]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Event]!!)
    }

    @Test
    fun deleteAllImplicitTest() {
        val query = Query("delete")
        assertEquals(Scope.Event, query.deleteScope)
        assertTrue(query.isImplicitSelectAll[Scope.Log]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Trace]!!)
        assertTrue(query.isImplicitSelectAll[Scope.Event]!!)
        assertTrue(query.selectAll[Scope.Log]!!)
        assertTrue(query.selectAll[Scope.Trace]!!)
        assertTrue(query.selectAll[Scope.Event]!!)
        assertTrue(query.selectStandardAttributes.values.all { it.isEmpty() })
        assertTrue(query.selectOtherAttributes.values.all { it.isEmpty() })
        assertTrue(query.selectExpressions.values.all { it.isEmpty() })
        assertFalse(query.isGroupBy[Scope.Log]!!)
        assertFalse(query.isGroupBy[Scope.Trace]!!)
        assertFalse(query.isGroupBy[Scope.Event]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Log]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Trace]!!)
        assertFalse(query.isImplicitGroupBy[Scope.Event]!!)
    }

    @Test
    fun deleteWithGroupByNotAllowed() {
        val ex = assertFailsWith<RecognitionException> {
            Query("delete event group by l:name")
        }
        assertTrue("mismatched input" in ex.message!!)
        assertTrue("group by" in ex.message!!)
        assertTrue("expecting" in ex.message!!)
    }

    @Test
    fun deleteWithSelectNotAllowed() {
        val ex = assertFailsWith<RecognitionException> {
            Query("select e:name delete event where l:name='abc'")
        }
        assertTrue("mismatched input" in ex.message!!)
        assertTrue("delete" in ex.message!!)
        assertTrue("expecting" in ex.message!!)
    }
}
