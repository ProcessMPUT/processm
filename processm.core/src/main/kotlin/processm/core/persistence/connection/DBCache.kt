package processm.core.persistence.connection

import com.github.benmanes.caffeine.cache.Caffeine
import processm.core.Brand
import processm.core.persistence.DBConnectionPool
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.read
import kotlin.concurrent.write

object DBCache {
    /**
     * Name to lock mapping
     */
    private val lockMap = ConcurrentHashMap<String, CacheKey>()

    /**
     * DB connections cache.
     * As key database's name and lock to prevent concurrent read and write action.
     */
    private val db = Caffeine.newBuilder()
        .removalListener<CacheKey, DBConnectionPool> { key, pool, _ ->
            key!!.lock.write {
                pool!!.close()
            }
        }
        .build<CacheKey, DBConnectionPool> { key ->
            DBConnectionPool(key.actualKey)
        }

    /**
     * Get database's pool from cache.
     * If not found record, add it.
     *
     * Access based on read lock.
     */
    fun get(identity: String): DBConnectionPool {
        lockMap.computeIfAbsent(identity) { CacheKey(identity) }.let { key ->
            key.lock.read { return db[key]!! }
        }
    }

    /**
     * Get main database's pool.
     */
    fun getMainDBPool(): DBConnectionPool = get(Brand.mainDBInternalName)
}