package processm.core.persistence.connection

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

/**
 * Creates the kotlin/exposed transaction in the main database.
 * This is an abbreviation for `transaction(DBCache.getMainDBPool().database) { /* body */ }`.
 */
inline fun <T> transactionMain(noinline statement: Transaction.() -> T): T =
    transaction(DBCache.getMainDBPool().database, statement)

/**
 * Creates the kotlin/exposed transaction in the datastore database.
 * This is an abbreviation for `transaction(DBCache.get(id.toString()).database) { /* body */ }`.
 */
inline fun <T> transaction(id: UUID, noinline statement: Transaction.() -> T): T = transaction(id.toString(), statement)

/**
 * Creates the kotlin/exposed transaction in the database named [id].
 * This is an abbreviation for `transaction(DBCache.get(id).database) { /* body */ }`.
 */
inline fun <T> transaction(id: String, noinline statement: Transaction.() -> T): T =
    transaction(DBCache.get(id).database, statement)

