package processm.core.log

import org.junit.jupiter.api.Tag
import processm.core.DBTestHelper.dbName
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.helpers.zipOrThrow
import processm.logging.logger
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Ignore
import kotlin.test.Test

class XESPerformanceTest {
    @Ignore
    @Test
    @Tag("performance")
    fun `Analyze logs from 4TU repository`() {
        try {
            File("../xes-logs/").walk().forEach { file ->
                if (file.canonicalPath.endsWith(".xes.gz")) {
                    println(file.canonicalPath)

                    DBXESOutputStream(DBCache.get(dbName).getConnection()).use { db ->
                        file.absoluteFile.inputStream().use { fileStream ->
                            GZIPInputStream(fileStream).use { stream ->
                                db.write(XMLXESInputStream(stream))
                            }
                        }
                    }

                    val dbInput = DBXESInputStream(dbName, Query(getLogId()))

                    file.absoluteFile.inputStream().use { fileStream ->
                        GZIPInputStream(fileStream).use { stream ->
                            (XMLXESInputStream(stream) zipOrThrow dbInput).all { (elem1, elem2) -> elem1 == elem2 }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger().warn("XES performance error", e)
            throw e
        }
    }

    private fun getLogId(): Int {
        DBCache.get(dbName).getConnection().use {
            val response = it.prepareStatement("""SELECT id FROM logs ORDER BY id DESC LIMIT 1""").executeQuery()
            response.next()

            return response.getInt("id")
        }
    }
}
