package processm.core.persistence

import processm.core.Brand

/**
 * Database-based durable storage.
 */
class DurablePersistenceProvider(dbName: String = Brand.mainDBInternalName) :
    AbstractPersistenceProvider(dbName, "durable_storage")
