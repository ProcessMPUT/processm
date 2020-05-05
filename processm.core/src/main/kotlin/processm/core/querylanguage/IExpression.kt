package processm.core.querylanguage

/**
 * Represents a PQL expression.
 */
interface IExpression {
    /**
     * The children expressions.
     */
    val children: Array<out IExpression>

    /**
     * The (raw) scope of this expression (excluding children). The value of null means that the scope is not specified
     * for this expression. This happens for e.g., operators, literals etc.
     */
    val scope: Scope?

    /**
     * The lowest scope in this expression.
     */
    val effectiveScope: Scope

    /**
     * Indicates whether this is a terminal node of an expression tree.
     */
    val isTerminal: Boolean

    /**
     * Line in the source PQL query.
     */
    val line: Int

    /**
     * Character position in line in the source PQL query.
     */
    val charPositionInLine: Int

    /**
     * Selects recursively from this expression subexpressions matching the given predicate. A child expression may be
     * selected even if its parent does not match this predicate.
     * @param predicate A predicate that given an expression yields either true or false to approve or deny this
     * expression, respectively.
     * @return The sequence of subexpressions.
     */
    fun filter(predicate: (expression: IExpression) -> Boolean): Sequence<IExpression>

    /**
     * Selects recursively from this expression subexpressions matching the given predicate. A child expression is not
     * selected if its parent does not match this predicate.
     * @param predicate A predicate that given an expression yields either true or false to approve or deny this
     * expression, respectively.
     * @return The sequence of subexpressions.
     */
    fun filterRecursively(predicate: (expression: IExpression) -> Boolean): Sequence<IExpression>
}