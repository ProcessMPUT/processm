package processm.core.querylanguage

/**
 * Represents a PQL expression.
 *
 * @property children The children expressions.
 */
@Suppress("SelfReferenceConstructorParameter")
open class Expression(vararg val children: Expression) {
    companion object {
        private val empty: Map<Scope, Expression> = mapOf(
            Scope.Log to Expression(),
            Scope.Trace to Expression(),
            Scope.Event to Expression()
        )

        /**
         * Returns an empty singleton [Expression] for the given scope.
         *
         * @param scope The scope for which to return the expression.
         */
        @Suppress("MapGetWithNotNullAssertionOperator")
        fun empty(scope: Scope = Scope.Event): Expression = empty[scope]!!
    }

    /**
     * The (raw) scope of this expression (excluding children). The value of null means that the scope is not specified
     * for this expression. This happens for e.g., operators, literals etc.
     */
    open val scope: Scope? = null

    /**
     * The deepest scope in this expression.
     */
    val effectiveScope: Scope by lazy(LazyThreadSafetyMode.NONE) {
        calculateEffectiveScope() ?: Scope.Event
    }

    /**
     * Calculates an effective scope (the deepest scope) for this expression. Yields null when the scope is not set for
     * this expression and all of its children.
     */
    protected open fun calculateEffectiveScope(): Scope? {
        var effectiveScope: Scope? = scope
        for (child in children) {
            val chScope = child.calculateEffectiveScope()
            if (chScope !== null && (effectiveScope === null || chScope > effectiveScope)) {
                effectiveScope = chScope
                if (effectiveScope == Scope.Event)
                    break
            }
        }
        return effectiveScope
    }

    /**
     * Indicates whether this is a terminal node of an expression tree.
     */
    val isTerminal: Boolean = children.isEmpty()

    /**
     * Line in the source PQL query.
     */
    open val line: Int
        get() = children.map { it.line }.min() ?: 0

    /**
     * Character position in line in the source PQL query.
     */
    open val charPositionInLine: Int
        get() = children.map { it.charPositionInLine }.min() ?: 0

    override fun toString(): String = buildString {
        children.forEach { append(it.toString()) }
    }
}