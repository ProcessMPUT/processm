package processm.dbmodels

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.transactions.TransactionManager
import processm.core.models.metadata.URN

internal class
ILikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "ILIKE")

infix fun <T : String?> ExpressionWithColumnType<T>.ilike(pattern: String): Op<Boolean> =
    ILikeOp(this, QueryParameter(pattern, columnType))

internal class IEq<T : String?>(expr1: Expression<T>, expr2: Expression<T>) :
    ComparisonOp(LowerCase<T>(expr1), LowerCase<T>(expr2), "=")

/**
 * Equals ignore case for string-like expressions.
 */
infix fun <T : String?> Expression<T>.ieq(other: Expression<T>): Op<Boolean> =
    IEq(this, other)

/**
 * Equals ignore case for string-like expressions.
 */
infix fun <T : String?> Expression<T>.ieq(other: T): Op<Boolean> =
    IEq(this, QueryParameter(other, TextColumnType()))


/**
 * Executes [action] on the given entity just after it was committed. [action] does not run on rollback.
 */
fun <T : Comparable<T>> Entity<T>.afterCommit(action: Entity<T>.() -> Unit) {
    TransactionManager.current().registerInterceptor(object : StatementInterceptor {
        override fun afterCommit(transaction: Transaction) {
            action()
        }
    })
}

/**
 * Universal Resource Name (URN) for the corresponding database row.
 * Intended for the use in access control lists.
 */
val Entity<*>.urn: URN
    get() = URN("urn:processm:db/${this.id.table.tableName}/${id.value}")
