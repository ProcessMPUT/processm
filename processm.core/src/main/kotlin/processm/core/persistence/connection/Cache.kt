package processm.core.persistence.connection

import com.github.benmanes.caffeine.cache.Caffeine
import processm.core.persistence.DBConnectionPool
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.read
import kotlin.concurrent.write

class Cache {
    private val lockMap = ConcurrentHashMap<UUID, CacheKey>()
    private val db = Caffeine.newBuilder()
        .removalListener<CacheKey, DBConnectionPool> { key, connection, _ ->
            key!!.lock.write {
                connection!!.getConnection().close()
            }
        }
        .build<CacheKey, DBConnectionPool> { key ->
            // FIXME
//            key.actualKey
            DBConnectionPool()
        }

    fun get(identity: UUID, synchronized: (db: DBConnectionPool) -> Unit) {
        lockMap.computeIfAbsent(identity) { CacheKey(identity) }.let { key ->
            key.lock.read {
                synchronized(db[key]!!)
            }
        }
    }
}