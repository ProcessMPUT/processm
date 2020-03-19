package processm.core.querylanguage

/**
 * Represents a function call in a PQL query.
 *
 * @property name The name of the function being called.
 */
class Function(
    val name: String,
    override val line: Int,
    override val charPositionInLine: Int,
    vararg arguments: Expression
) :
    Expression(*arguments) {
    companion object {
        private val scalarFunctions = mapOf(
            "date" to 1,
            "time" to 1,
            "year" to 1,
            "month" to 1,
            "day" to 1,
            "hour" to 1,
            "minute" to 1,
            "second" to 1,
            "millisecond" to 1,
            "quarter" to 1,
            "dayofweek" to 1,
            "now" to 0,
            "upper" to 1,
            "lower" to 1,
            "round" to 1
        )

        private val aggregationFunctions = mapOf(
            "min" to 1,
            "max" to 1,
            "avg" to 1,
            "count" to 1,
            "sum" to 1
        )
    }

    /**
     * The type of this function.
     */
    val type: FunctionType

    init {
        type = when (name) {
            in scalarFunctions -> FunctionType.Scalar
            in aggregationFunctions -> FunctionType.Aggregation
            else -> throw IllegalArgumentException("Line $line position $charPositionInLine: Call of an undefined function $name.")
        }
        val validArguments = scalarFunctions[name] ?: aggregationFunctions[name]
        if (children.size != validArguments)
            throw IllegalArgumentException("Line $line position $charPositionInLine: Invalid number of arguments supplied to function $name: ${children.size} given, $validArguments expected.")
    }

    override fun toString(): String = "$name(${children.joinToString(", ")})"
}