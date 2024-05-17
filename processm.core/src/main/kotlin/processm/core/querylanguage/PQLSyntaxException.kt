package processm.core.querylanguage

import processm.helpers.AbstractLocalizedException
import processm.logging.logger
import java.util.*

/**
 * A syntax error in PQL other than [PQLParserException]
 */
class PQLSyntaxException(
    val problem: Problem,
    val line: Int,
    val charPositionInLine: Int,
    vararg val args: Any
) : AbstractLocalizedException(
    "Line $line position $charPositionInLine: $problem $args"
) {
    override fun localizedMessage(locale: Locale): String = try {
        val prefixFormatString = getFormatString(locale, "PQLErrorPrefix")
        val formatString = getFormatString(locale, problem.toString())
        String.format(locale, prefixFormatString, line, charPositionInLine) +
                ": " +
                String.format(locale, formatString, *args)
    } catch (e: Exception) {
        logger().error("An exception was thrown while preparing localized exception", e)
        message ?: problem.toString()
    }

    enum class Problem {
        AggregationFunctionInWhere,
        ClassifierInWhere,
        DuplicateLimit,
        DuplicateOffset,
        PositiveIntegerRequired,
        DecimalPartDropped,
        ScopeRequired,
        ScopeHoistingInSelectOrOrderBy,
        UnexpectedChild,
        ExplicitSelectAllWithImplicitGroupBy,
        MissingAttributesInAggregation,
        SelectAllConflictsWithReferencingByName,
        MixedScopes,
        AttributeNotInGroupBy,
        OrderByClauseRemoved,
        ClassifierOnLog,
        NoHoistingBeyondLong,
        UnknownAttributeType,
        NoSuchAttribute,
        InvalidBoolean,
        InvalidNumber,
        InvalidDateTime,
        InvalidUUID
    }
}