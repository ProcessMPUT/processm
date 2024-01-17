package processm.core.persistence

import java.net.URI
import kotlin.reflect.KClass

/**
 * In-memory cache. The references to the stored objects are deleted when this object becomes garbage-collected.
 */
class VolatilePersistenceProvider : PersistenceProvider {
    private val map = HashMap<URI, Any>()

    override fun put(uri: URI, obj: Any) {
        map[uri] = obj
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T>get(uri: URI, klass: KClass<*>): T {
        return map[uri] as T ?: throw IllegalArgumentException("Nothing found for URI $uri")
    }

    override fun delete(uri: URI) {
        map.remove(uri) ?: throw IllegalArgumentException("Nothing found for URI $uri")
    }
}