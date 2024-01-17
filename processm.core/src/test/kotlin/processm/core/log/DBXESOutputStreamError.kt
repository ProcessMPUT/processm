package processm.core.log

import processm.core.DBTestHelper
import processm.core.persistence.connection.DBCache
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * This class contains a single test that happens to be very time-consuming. It was extracted here to improve
 * parallelism of the tests.
 */
class DBXESOutputStreamError {
    /**
     * Demonstrates the error: "org.postgresql.util.PSQLException: ERROR: index row size 2792 exceeds btree version 4
     * maximum 2704 for index "_hyper_7_2_chunk_logs_attributes_key"" when inserting XES file into the database.
     * See #101
     */
    @Ignore
    @Test
    fun `PSQLException ERROR index row size 2792 exceeds btree version 4 maximum 2704 for index`() {
        val LOG_FILE = File("../xes-logs/Hospital_log.xes.gz")
        GZIPInputStream(FileInputStream(LOG_FILE)).use { gzip ->
            DBXESOutputStream(DBCache.get(DBTestHelper.dbName).getConnection()).use { out ->
                out.write(XMLXESInputStream(gzip))
            }
        }
    }
}
