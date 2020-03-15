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
    open val effectiveScope: Scope by lazy(LazyThreadSafetyMode.NONE) {
        var effectiveScope = scope
        fun process(expression: Expression) {
            for (child in expression.children) {
                if (effectiveScope === null || child.scope !== null && child.scope!! > effectiveScope!!) {
                    effectiveScope = child.scope
                    if (effectiveScope == Scope.Event)
                        return
                }
                process(child)
            }
        }
        process(this)
        effectiveScope ?: Scope.Event
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