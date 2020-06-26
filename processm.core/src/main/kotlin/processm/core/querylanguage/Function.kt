package processm.core.querylanguage

/**
 * Represents a function call in a PQL query.
 *
 * @property name The name of the function being called.
 */
class Function(
    _name: String,
    override val line: Int,
    override val charPositionInLine: Int,
    vararg arguments: Expression
) :
    Expression(*arguments) {
    companion object {
        private val pqlFunctionPattern = Regex("^(?:(l(?:og)?|t(?:race)?|e(?:vent)?):)?(.+)$")

        private val scalarFunctions = mapOf(
            "date" to 1.toByte(),
            "time" to 1.toByte(),
            "year" to 1.toByte(),
            "month" to 1.toByte(),
            "day" to 1.toByte(),
            "hour" to 1.toByte(),
            "minute" to 1.toByte(),
            "second" to 1.toByte(),
            "millisecond" to 1.toByte(),
            "quarter" to 1.toByte(),
            "dayofweek" to 1.toByte(),
            "now" to 0.toByte(),
            "upper" to 1.toByte(),
            "lower" to 1.toByte(),
            "round" to 1.toByte()
        )

        private val aggregationFunctions = mapOf(
            "min" to 1.toByte(),
            "max" to 1.toByte(),
            "avg" to 1.toByte(),
            "count" to 1.toByte(),
            "sum" to 1.toByte()
        )
    }

    val name: String

    override val scope: Scope?

    init {
        val match = pqlFunctionPattern.matchEntire(_name)
        assert(match !== null)

        scope = match!!.groups[1]?.let { Scope.parse(it.value) }
        name = match.groups[2]!!.value
    }

    override val type: Type
        get() = when (name) {
            "date", "time", "now" -> Type.Datetime
            "year", "month", "day", "hour", "minute", "second", "millisecond", "quarter", "dayofweek",
            "round", "avg", "sum", "count" -> Type.Number
            "upper", "lower" -> Type.String
            "min", "max" -> children.map { it.type }.first()  // depends on children' types
            else -> throwUndefined()
        }

    override val expectedChildrenTypes: Array<Type> = when (name) {
        "date", "time", "year", "month", "day", "hour", "minute", "second", "millisecond", "quarter", "dayofweek"
        -> arrayOf(Type.Datetime)
        "round", "avg", "sum" -> arrayOf(Type.Number)
        "count" -> arrayOf(Type.Any)
        "upper", "lower" -> arrayOf(Type.String)
        "min", "max" -> arrayOf(Type.Any)
        "now" -> emptyArray()
        else -> throwUndefined()
    }

    /**
     * The type of this function.
     */
    val functionType: FunctionType = when (name) {
        in scalarFunctions -> FunctionType.Scalar
        in aggregationFunctions -> FunctionType.Aggregation
        else -> throwUndefined()
    }

    init {
        // validate scope
        if (scope !== null && functionType == FunctionType.Scalar) {
            require(children.all { it.effectiveScope <= scope }) {
                "Line $line position $charPositionInLine: The scope of the scalar function $name must be not greater than the effective scope of its arguments."
            }
        }

        // validate arguments
        val validArguments = scalarFunctions[name] ?: aggregationFunctions[name]
        require(children.size.toByte() == validArguments) {
            "Line $line position $charPositionInLine: Invalid number of arguments supplied to function $name: ${children.size} given, $validArguments expected."
        }
    }

    override fun toString(): String = "${scope.prefix}$name(${children.joinToString(", ")})"

    private fun throwUndefined(): Nothing =
        throw IllegalArgumentException("Line $line position $charPositionInLine: Call of an undefined function $name.")
}