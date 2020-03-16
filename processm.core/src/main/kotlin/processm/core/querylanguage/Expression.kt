package processm.core.querylanguage

/**
 * Represents a PQL expression.
 *
 * @property children The children expressions.
 */
@Suppress("SelfReferenceConstructorParameter")
open class Expression(vararg val children: Expression) {
    companion object {
        /**
         * An empty singleton [Expression].
         */
        val empty: Expression = Expression()
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

    /**
     * Selects recursively from this expression subexpressions matching the given predicate. A child expression may be
     * selected even if its parent does not match this predicate.
     * @param predicate A predicate that given an expression yields either true or false to approve or deny this
     * expression, respectively.
     * @return The sequence of subexpressions.
     */
    fun filter(predicate: (expression: Expression) -> Boolean): Sequence<Expression> = sequence {
        if (predicate(this@Expression))
            yield(this@Expression)

        for (child in children)
            yieldAll(child.filter(predicate))
    }

    /**
     * Selects recursively from this expression subexpressions matching the given predicate. A child expression is not
     * selected if its parent does not match this predicate.
     * @param predicate A predicate that given an expression yields either true or false to approve or deny this
     * expression, respectively.
     * @return The sequence of subexpressions.
     */
    fun filterRecursively(predicate: (expression: Expression) -> Boolean): Sequence<Expression> = sequence {
        if (predicate(this@Expression)) {
            yield(this@Expression)
            for (child in children)
                yieldAll(child.filterRecursively(predicate))
        }
    }

    override fun toString(): String = children.joinToString("")
}