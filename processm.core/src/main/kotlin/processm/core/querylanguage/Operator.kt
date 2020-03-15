package processm.core.querylanguage

/**
 * Represents an operator in a PQL query.
 *
 * @property value A textual representation of this operator.
 * @property line A line number where this operator occurred in the PQL query.
 * @property charPositionInLine A character position in line where this operator occurred in the PQL query.
 */
class Operator(val value: String, override val line: Int, override val charPositionInLine: Int) : Expression() {
    override fun toString(): String = value
}