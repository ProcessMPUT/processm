package processm.dbmodels

import org.jetbrains.exposed.sql.*

internal class
ILikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "ILIKE")

infix fun <T : String?> ExpressionWithColumnType<T>.ilike(pattern: String): Op<Boolean> =
    ILikeOp(this, QueryParameter(pattern, columnType))