package processm.core.persistence

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import processm.core.persistence.connection.DBCache
import processm.helpers.serialization.SerializersModuleProvider
import java.net.URI
import java.util.*
import kotlin.reflect.KClass

/**
 * Base class for database-based persistence providers.
 *
 * This class is not thread safe.
 */
@Suppress("SqlResolve")
abstract class AbstractPersistenceProvider(
    protected val dbName: String,
    protected val tableName: String
) : PersistenceProvider, AutoCloseable {

    protected val connection = DBCache.get(dbName).getConnection()

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
    private val json = Json {
        allowStructuredMapKeys = true
        serializersModule = SerializersModule {
            for (provider in ServiceLoader.load(SerializersModuleProvider::class.java)) {
                include(provider.getSerializersModule())
            }
        }
    }

    init {
        assert(connection.autoCommit)
    }

    @OptIn(InternalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun put(uri: URI, obj: Any) {
        insert.setString(1, uri.toString())
        val serializer = obj::class.serializer() as KSerializer<Any>
        insert.setString(2, json.encodeToString(serializer, obj))
        insert.execute()
    }

    @OptIn(InternalSerializationApi::class)
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
