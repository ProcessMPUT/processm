package processm.core.persistence

import kotlinx.serialization.ImplicitReflectionSerializer

/**
 * Database-based durable storage.
 */
@ImplicitReflectionSerializer
class DurablePersistenceProvider : AbstractPersistenceProvider("durable_storage") {
}