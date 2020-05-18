package processm.core.querylanguage

/**
 * Represents an operator in a PQL query.
 *
 * @property value A textual representation of this operator.
 * @property line A line number where this operator occurred in the PQL query.
 * @property charPositionInLine A character position in line where this operator occurred in the PQL query.
 */
class Operator(
    val value: String,
    override val line: Int,
    override val charPositionInLine: Int,
    vararg children: IExpression
) : Expression(*children) {

    override val type: Type
        get() = when (value) {
            "*", "/" -> Type.Number
            "like", "matches",
            "and", "or", "not",
            "=", "!=", "<", "<=", ">", ">=",
            "is null", "is not null", "in", "not in" -> Type.Boolean
            "+", "-" -> if (children[0].type != Type.Unknown) children[0].type else children[1].type
            else -> throwUndefined()
        }

    override val expectedChildrenTypes: Array<Type> = when (value) {
        "*", "/" -> arrayOf(Type.Number, Type.Number)
        "+" -> when (if (children[0].type != Type.Unknown) children[0].type else children[1].type) {
            Type.Number -> arrayOf(Type.Number, Type.Number)
            Type.String -> arrayOf(Type.String, Type.String)
            else -> arrayOf(Type.Any, Type.Any).also { assert(children.all { it.type == Type.Unknown }) }
        }
        "-" -> when (if (children[0].type != Type.Unknown) children[0].type else children[1].type) {
            Type.Number -> arrayOf(Type.Number, Type.Number)
            Type.Datetime -> arrayOf(Type.Datetime, Type.Datetime)
            else -> arrayOf(Type.Any, Type.Any).also { assert(children.all { it.type == Type.Unknown }) }
        }
        "like", "matches" -> arrayOf(Type.String, Type.String)
        "and", "or" -> arrayOf(Type.Boolean, Type.Boolean)
        "not" -> arrayOf(Type.Boolean)
        "=", "!=", "<", "<=", ">", ">=" -> arrayOf(Type.Any, Type.Any)
        "is null", "is not null" -> arrayOf(Type.Any)
        "in", "not in" -> Array<Type>(children.size + 1) { Type.Any }
        else -> throwUndefined()
    }

    val operatorType: OperatorType
        get() = when (value) {
            "*", "/", "+", "-",
            "like", "matches",
            "and", "or",
            "=", "!=", "<", "<=", ">", ">=",
            "in", "not in" -> OperatorType.Infix
            "not" -> OperatorType.Prefix
            "is null", "is not null" -> OperatorType.Postfix
            else -> throwUndefined()
        }

    override fun toString(): String = when (operatorType) {
        OperatorType.Prefix -> children.joinToString(" ", prefix = value + " ")
        OperatorType.Infix -> children.joinToString(" $value ")
        OperatorType.Postfix -> children.joinToString(" ", postfix = " " + value)
    }

    private fun throwUndefined(): Nothing =
        throw IllegalArgumentException("Line $line position $charPositionInLine: Use of an undefined operator $value.")
}