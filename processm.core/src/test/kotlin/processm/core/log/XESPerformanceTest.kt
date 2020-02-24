package processm.core.log

import org.junit.jupiter.api.Tag
import processm.core.helpers.zipOrThrow
import processm.core.logging.logger
import processm.core.persistence.DBConnectionPool
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test

class XESPerformanceTest {
    @Test
    @Tag("performance")
    fun `Analyze logs from 4TU repository`() {
        try {
            File("../xes-logs/").walk().forEach { file ->
                if (file.canonicalPath.endsWith(".xes.gz")) {
                    println(file.canonicalPath)

                    DatabaseXESOutputStream().use { db ->
                        file.absoluteFile.inputStream().use { fileStream ->
                            GZIPInputStream(fileStream).use { stream ->
                                db.write(XMLXESInputStream(stream))
                            }
                        }
                    }

                    val dbInput = DatabaseXESInputStream(getLogId())

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
        DBConnectionPool.getConnection().use {
            val response = it.prepareStatement("""SELECT id FROM logs ORDER BY id DESC LIMIT 1""").executeQuery()
            response.next()

            return response.getInt("id")
        }
    }
}