package processm.core.querylanguage


open class Expression(vararg val children: Expression) {
    /**
     * The (raw) scope of this expression (excluding children).
     */
    open val scope: Scope = Scope.Log

    /**
     * The deepest scope in this expression.
     */
    val effectiveScope: Scope
        get() {
            var effectiveScope = scope
            for (child in children)
                if (child.effectiveScope > effectiveScope)
                    effectiveScope = child.effectiveScope
            return effectiveScope
        }

    /**
     * Indicates whether this is a terminal node of an expression tree.
     */
    val isTerminal: Boolean = children.isEmpty()

    override fun toString(): String = buildString {
        children.forEach { append(it.toString()) }
    }
}