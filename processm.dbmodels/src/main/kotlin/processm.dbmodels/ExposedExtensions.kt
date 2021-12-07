package processm.dbmodels

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.transactions.TransactionManager

internal class
ILikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "ILIKE")

infix fun <T : String?> ExpressionWithColumnType<T>.ilike(pattern: String): Op<Boolean> =
    ILikeOp(this, QueryParameter(pattern, columnType))

/**
 * Executes [action] on the given entity just after it was committed. [action] does not run on rollback.
 */
fun <T : Comparable<T>> Entity<T>.afterCommit(action: Entity<T>.() -> Unit) {
    TransactionManager.current().registerInterceptor(object : StatementInterceptor {
        override fun afterCommit() {
            action()
        }
    })
}
