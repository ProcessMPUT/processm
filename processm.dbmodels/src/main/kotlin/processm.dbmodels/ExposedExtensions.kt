package processm.dbmodels

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.transactions.TransactionManager
import processm.core.helpers.isUUID
import processm.core.helpers.toUUID
import processm.core.models.metadata.URN
import processm.dbmodels.models.DataStores
import processm.dbmodels.models.Workspaces
import java.util.*

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
    get() = id.urn

/**
 * Universal Resource Name (URN) for the corresponding database row.
 * Intended for the use in access control lists.
 */
val EntityID<*>.urn: URN
    get() = URN("urn:processm:db/${this.table.tableName}/${value}")

/**
 * A list of tables [decode] can recognize. Must be expanded if a new class of objects is introduced to ACLs.
 */
private val tables = listOf(Workspaces, DataStores)

/**
 * Decodes the URN returned by [urn] back to [EntityID].
 * Currently, it is assumed that the entity uses UUID as the id.
 */
fun URN.decode(): EntityID<UUID> {
    val firstSlash = urn.indexOf('/')
    require(firstSlash >= 0)
    val lastSlash = urn.lastIndexOf('/')
    require(firstSlash + 1 < lastSlash)
    val prefix = urn.substring(0, firstSlash)
    require(prefix == "urn:processm:db")
    val tableName = urn.substring(firstSlash + 1, lastSlash)
    val table = tables.first { it.tableName == tableName }
    val entityID = urn.substring(lastSlash + 1)
    require(entityID.isUUID()) { "Entity ID `$entityID` is not a UUID" }
    return EntityIDFunctionProvider.createEntityID(entityID.toUUID()!!, table)
}