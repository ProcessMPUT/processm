package processm.core.log.hierarchical

import processm.core.querylanguage.Scope

internal data class ScopeWithMetadata(val scope: Scope, val hoisting: Int) {
    val table: String
        get() = scope.table
    val alias: String = (scope.shortName + if (hoisting != 0) hoisting else "").intern()
}

internal val Scope.table: String
    get() = when (this) {
        Scope.Log -> "logs"
        Scope.Trace -> "traces"
        Scope.Event -> "events"
    }

internal val Scope.alias: String
    get() = this.shortName
