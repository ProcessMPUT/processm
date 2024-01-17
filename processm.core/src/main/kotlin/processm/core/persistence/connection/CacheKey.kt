package processm.core.persistence.connection

import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * DB connection cache key.
 * As key object with name and lock.
 *
 * Lock with fair ordering policy (see [ReentrantReadWriteLock]).
 */
data class CacheKey(val actualKey: String) {
    val lock: ReentrantReadWriteLock = ReentrantReadWriteLock(true)
}