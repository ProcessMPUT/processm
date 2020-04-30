package processm.core.querylanguage

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField

/**
 * Represents a literal in a PQL query. The subclasses of this class hold particular types of literals.
 */
@Suppress("LeakingThis")
sealed class Literal<out T>(literal: String, override val line: Int, override val charPositionInLine: Int) :
    Expression() {
    companion object {
        private val scopePattern: Regex = Regex("^(?:(l(?:og)?|t(?:race)?|e(?:vent)?):)?(.+)$")
    }

    final override val scope: Scope?

    /**
     * The parsed value of the literal. This property is stronly-typed.
     */
    val value: T

    init {
        val match = scopePattern.matchEntire(literal)
        assert(match !== null)
        scope = with(match!!.groups[1]) {
            when (this) {
                null -> null
                else -> Scope.parse(this.value)
            }
        }
        value = parse(match.groups[2]!!.value)
    }

    /**
     * Parses the given literal to its value in the literal-specific type [T].
     *
     * @return The parsed value.
     */
    protected abstract fun parse(literal: String): T

    override fun toString(): String = value.toString()
}

/**
 * Represents a string literal in a PQL query.
 */
class StringLiteral(literal: String, line: Int, charPositionInLine: Int) :
    Literal<String>(literal, line, charPositionInLine) {
    override fun parse(literal: String): String {
        require((literal[0] == '"' || literal[0] == '\'') && literal[0] == literal[literal.length - 1]) {
            "Line $line position $charPositionInLine: Invalid format of string literal: $literal."
        }

        return literal.substring(1, literal.length - 1)
    }
}

/**
 * Represents a datetime literal in a PQL query.
 */
class DateTimeLiteral(literal: String, line: Int, charPositionInLine: Int) :
    Literal<Instant>(literal, line, charPositionInLine) {
    companion object {
        private val parsers = arrayOf(
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .optionalStart()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .optionalEnd()
                .optionalStart()
                .appendLiteral('T')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .optionalEnd()
                .optionalStart()
                .appendOffsetId()
                .optionalEnd()
                .parseDefaulting(ChronoField.YEAR, 1970)
                .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
                .toFormatter(),
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .optionalStart()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .optionalEnd()
                .optionalStart()
                .appendLiteral('T')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .optionalEnd()
                .optionalStart()
                .appendOffset("+HHmmss", "Z")
                .optionalEnd()
                .parseDefaulting(ChronoField.YEAR, 1970)
                .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
                .toFormatter(),
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendValue(ChronoField.YEAR, 4)
                .appendValue(ChronoField.MONTH_OF_YEAR, 2)
                .appendValue(ChronoField.DAY_OF_MONTH, 2)
                .optionalStart()
                .optionalStart()
                .appendLiteral("T")
                .optionalEnd()
                .appendValue(ChronoField.HOUR_OF_DAY, 2)
                .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                .optionalStart()
                .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                .optionalStart()
                .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                .optionalEnd()
                .optionalEnd()
                .optionalEnd()
                .optionalStart()
                .parseLenient()
                .appendOffset("+HHMMss", "Z")
                .optionalEnd()
                .parseDefaulting(ChronoField.YEAR, 1970)
                .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
                .toFormatter()
        )
    }

    override fun parse(literal: String): Instant {
        val datetime = literal.substring(1)
        var exception: DateTimeParseException? = null
        for (parser in parsers) {
            try {
                val result = parser.runCatching { parse(datetime, ZonedDateTime::from).toInstant() }
                return result.getOrNull() ?: parser.parse(datetime, LocalDateTime::from)?.toInstant(ZoneOffset.UTC)!!
            } catch (e: DateTimeParseException) {
                if (exception === null)
                    exception = e
                else
                    exception.addSuppressed(e)
            }
        }
        throw IllegalArgumentException(
            "Line $line position $charPositionInLine: Invalid format of datetime literal: $literal.",
            exception!!
        )
    }
}

/**
 * Represents a number literal in a PQL query.
 */
class NumberLiteral(literal: String, line: Int, charPositionInLine: Int) :
    Literal<Double>(literal, line, charPositionInLine) {
    override fun parse(literal: String): Double = literal.toDouble()
}

/**
 * Represents a boolean literal in a PQL query.
 */
class BooleanLiteral(literal: String, line: Int, charPositionInLine: Int) :
    Literal<Boolean>(literal, line, charPositionInLine) {
    override fun parse(literal: String): Boolean = when (literal) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException(
            "Line $line position $charPositionInLine: Invalid format of boolean literal: $literal."
        )
    }
}

/**
 * Represents a null literal in a PQL query.
 */
class NullLiteral(literal: String, line: Int, charPositionInLine: Int) :
    Literal<Any?>(literal, line, charPositionInLine) {
    override fun parse(literal: String): Any? = null
}