package processm.core

import processm.core.log.DBXESOutputStream
import processm.core.log.InferConceptInstanceFromStandardLifecycle
import processm.core.log.XESComponent
import processm.core.log.XMLXESInputStream
import processm.core.persistence.connection.DBCache
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.zip.GZIPInputStream

object DBTestHelper {
    /**
     * Test database name.
     * Reduces warm up time in tests by performing all tests in the same database.
     * The test database name always has the form of 00000000-0000-ffff-<lower 64 bits>.
     */
    val dbName: String = UUID(0xFFFFFL, System.currentTimeMillis()).toString()

    /**
     * The UUID of the JournalReview-extra.xes log.
     */
    val JournalReviewExtra by lazy {
        this::class.java.getResourceAsStream("/xes-logs/JournalReview-extra.xes").use { loadLog(it) }
    }

    /**
     * The UUID of the bpi_challenge_2013_open_problems.xes.gz log.
     */
    val BPIChallenge2013OpenProblems by lazy {
        loadLog(File("../xes-logs/bpi_challenge_2013_open_problems.xes.gz"))
    }

    /**
     * The UUID of the log containing the first 2000 XES components of the Hospital_log.xes.gz log.
     */
    val HospitalLog by lazy {
        loadLog(File("../xes-logs/Hospital_log.xes.gz"), 2000)
    }

    /**
     * Parses the XES log from the given stream and inserts it into database [dbName].
     * @param limit The maximum number of [XESComponent]s to read from the [stream]. A negative value means no limit.
     * @return The identity:id of the inserted log.
     */
    fun loadLog(stream: InputStream, limit: Int = -1): UUID {
        val uuid = UUID.randomUUID()
        DBXESOutputStream(DBCache.get(dbName).getConnection()).use { output ->
            var xesInput: Sequence<XESComponent> = InferConceptInstanceFromStandardLifecycle(XMLXESInputStream(stream))
            if (limit >= 0)
                xesInput = xesInput.take(limit)

            output.write(xesInput.map {
                if (it is processm.core.log.Log) /* The base class for log */
                    it.identityId = uuid
                it
            })
        }
        return uuid
    }

    /**
     * Parses the XES log from the given xes.gz file and inserts it into database [dbName].
     * @param limit The maxium number of [XESComponent]s to read from the [file]. A negative value means no limit.
     * @return The identity:id of the inserted log.
     */
    fun loadLog(file: File, limit: Int = -1): UUID =
        file.inputStream().use { raw ->
            GZIPInputStream(raw).use { gzip ->
                loadLog(gzip, limit)
            }
        }
}
