package processm.core.persistence

import kotlinx.serialization.KSerializer
import kotlinx.serialization.UnsafeSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import processm.core.persistence.connection.DBCache
import java.net.URI
import kotlin.reflect.KClass

/**
 * Base class for database-based persistence providers.
 *
 * This class is not thread safe.
 */
@UnsafeSerializationApi
@Suppress("SqlResolve")
abstract class AbstractPersistenceProvider(protected val tableName: String) : PersistenceProvider, AutoCloseable {

    protected val connection = DBCache.getMainDBPool().getConnection()

    private val insert by lazy {
        connection.prepareStatement(
            """INSERT INTO $tableName(urn, data) VALUES (?, ?::json)
            ON CONFLICT (urn) DO UPDATE SET urn=EXCLUDED.urn, data=EXCLUDED.data"""
        )
    }
    private val select by lazy {
        connection.prepareStatement("SELECT data FROM $tableName WHERE urn=?")
    }
    private val delete by lazy {
        connection.prepareStatement("DELETE FROM $tableName WHERE urn=?")
    }
    private val json = Json { allowStructuredMapKeys = true }

    init {
        assert(connection.autoCommit)
    }

    @Suppress("UNCHECKED_CAST")
    override fun put(uri: URI, obj: Any) {
        insert.setString(1, uri.toString())
        val serializer = obj::class.serializer() as KSerializer<Any>
        insert.setString(2, json.encodeToString(serializer, obj))
        insert.execute()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(uri: URI, klass: KClass<*>): T {
        select.setString(1, uri.toString())
        select.execute()
        if (!select.resultSet.next())
            throw IllegalArgumentException("Nothing found for URI $uri")
        return json.decodeFromString(klass.serializer(), select.resultSet.getString(1)) as T
    }

    override fun delete(uri: URI) {
        delete.setString(1, uri.toString())
        delete.execute()
        if (delete.updateCount <= 0)
            throw IllegalArgumentException("Nothing found for URI $uri")
    }

    override fun close() {
        connection.close()
    }
}
