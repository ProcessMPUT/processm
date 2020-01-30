package processm.core.persistence

import java.net.URI
import kotlin.reflect.KClass

/**
 * A persistence provider interface. An implementing class is free to implement any guarantees for storage and retention
 * policy.
 */
interface PersistenceProvider {

    /**
     * Stores the given object under the given URI, possibly overwriting the object previously identified with the same
     * URI.
     * @param uri URI to store the object on.
     * @param obj The object to store.
     */
    fun put(uri: URI, obj: Any)

    /**
     * Retrieves the object identified by the given URI.
     * @param uri URI of the object to retrieve.
     * @exception IllegalArgumentException Exception thrown when the object is not found.
     */
    fun <T>get(uri: URI, klass: KClass<*>): T

    /**
     * Removes the object identified by the given URI.
     * @param uri URI of the object to remove.
     * @exception IllegalArgumentException Exception thrown when the object is not found.
     */
    fun delete(uri: URI)

}

inline fun <reified T> PersistenceProvider.get(uri: URI) = get<T>(uri, T::class)