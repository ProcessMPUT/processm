package processm.core.persistence.connection

import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * DB connection cache key.
 * As key object with UUID and lock.
 *
 * Lock with fair ordering policy (see [ReentrantReadWriteLock]).
 */
data class CacheKey(val actualKey: UUID) {
    val lock: ReentrantReadWriteLock = ReentrantReadWriteLock(true)
}