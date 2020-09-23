package processm.core.persistence

import kotlinx.serialization.UnsafeSerializationApi

/**
 * Database-based durable storage.
 */
@UnsafeSerializationApi
class DurablePersistenceProvider : AbstractPersistenceProvider("durable_storage")