package processm.core.querylanguage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScopeTests {

    @Test
    fun storeAndParseTest() {
        val scopes = arrayOf(Scope.Log, Scope.Trace, Scope.Event)
        for (scope in scopes) {
            assertEquals(scope, Scope.parse(scope.toString()))
            assertEquals(scope, Scope.parse(scope.name))
            assertEquals(scope, Scope.parse(scope.shortName))
        }
    }

    @Test
    fun invalidScopeParseTest() {
        assertFailsWith<IllegalArgumentException> { Scope.parse("XYZ") }
    }

    @Test
    fun lowerAndUpperTest() {
        val scopes = arrayOf(Scope.Log, Scope.Trace, Scope.Event)
        for (scope in scopes) {
            assertEquals(scope, scope.lower?.upper ?: Scope.Event)
            assertEquals(scope, scope.upper?.lower ?: Scope.Log)
        }
    }
}