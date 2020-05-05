package processm.core.querylanguage

/**
 * An expression with order direction. Intended for use in the order by clause. It implements a decorator on an actual
 * [Expression] with delegations. Use the [base] property to access the actual [Expression].
 *
 * @property base The backing expression.
 * @property direction The order direction.
 */
class OrderedExpression(val base: Expression, val direction: OrderDirection = OrderDirection.Ascending) :
    IExpression by base {

    override fun toString(): String = "$base $direction"
}