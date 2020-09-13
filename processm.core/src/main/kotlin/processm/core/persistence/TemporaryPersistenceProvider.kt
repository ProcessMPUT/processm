package processm.core.persistence

import kotlinx.serialization.UnsafeSerializationApi

/**
 * Database-based cache. The cached objects are deleted on close() call. This class is not thread safe.
 * @see close
 */
@UnsafeSerializationApi
class TemporaryPersistenceProvider : AbstractPersistenceProvider("temporary_storage") {
    init {
        connection.autoCommit = false
        connection.createStatement().execute(
            """CREATE TEMP TABLE $tableName(
                        urn VARCHAR(1024) NOT NULL PRIMARY KEY, 
                        data JSON NOT NULL)
                    ON COMMIT DROP"""
        )
    }
}