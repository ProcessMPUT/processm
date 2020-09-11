package processm.core.persistence.connection

import com.github.benmanes.caffeine.cache.Caffeine
import processm.core.persistence.DBConnectionPool
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.read
import kotlin.concurrent.write

object DBCache {
    private val lockMap = ConcurrentHashMap<String, CacheKey>()
    private val db = Caffeine.newBuilder()
        .removalListener<CacheKey, DBConnectionPool> { key, pool, _ ->
            key!!.lock.write {
                pool!!.close()
            }
        }
        .build<CacheKey, DBConnectionPool> { key ->
            DBConnectionPool(key.actualKey)
        }

    fun get(identity: String): DBConnectionPool {
        lockMap.computeIfAbsent(identity) { CacheKey(identity) }.let { key ->
            key.lock.read { return db[key]!! }
        }
    }
}