package processm.services.logic

import de.odysseus.staxon.json.JsonXMLConfig
import de.odysseus.staxon.json.JsonXMLConfigBuilder
import de.odysseus.staxon.json.JsonXMLOutputFactory
import org.apache.commons.io.input.BoundedInputStream
import processm.core.Brand
import processm.core.log.DBXESOutputStream
import processm.core.log.XMLXESInputStream
import processm.core.log.XMLXESOutputStream
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.toFlatSequence
import processm.core.logging.loggedScope
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.services.api.models.QueryResultCollectionMessageBody
import java.io.*
import java.nio.charset.Charset
import java.util.*
import java.util.zip.*
import javax.xml.stream.XMLOutputFactory

class LogsService {
    companion object {
        private const val xesFileInputSizeLimit = 5_000_000L
        private const val logLimit = 10L
        private const val traceLimit = 30L
        private const val eventLimit = 90L
        private const val downloadLimitFactor = 10L
    }

    private fun InputStream.boundStreamSize(streamSizeLimit: Long) =
        BufferedInputStream(BoundedInputStream(this, streamSizeLimit))

    /**
     * Returns all data sources for the specified [organizationId].
     */
    fun saveLogFile(dataSourceId: UUID, fileName: String?, logStream: InputStream) {
        loggedScope { logger ->
            logger.info("Saving new log file ($fileName) to $dataSourceId")
            DBXESOutputStream(
                DBCache.get(dataSourceId.toString()).getConnection()
            ).use { db ->
                db.write(
                    XMLXESInputStream(
                        if (fileName?.endsWith("gz") == true) GZIPInputStream(
                            logStream.boundStreamSize(xesFileInputSizeLimit)
                        )
                        else logStream.boundStreamSize(xesFileInputSizeLimit)
                    )
                )
            }
        }
    }

    /**
     * Create new data source named [name] and assigned to the specified [organizationId].
     */
    fun queryDataSourceJSON(dataSourceId: UUID, query: String): OutputStream.() -> Unit {
        // All preparation must be done here rather than in the returned lambda, as the lambda will be invoked
        // when writing output stream and error messages (e.g., parse errors) cannot be returned through HTTP
        // from that stage of processing.
        val queryStream = createQueryStream(dataSourceId, query)

        return {
            val config: JsonXMLConfig =
                JsonXMLConfigBuilder()
                    .autoArray(true)
                    .autoPrimitive(true)
                    .build()
            val factory = JsonXMLOutputFactory(config)

            write("{\"${QueryResultCollectionMessageBody::data.name}\":[".toByteArray())
            val logsIterator = queryStream.iterator()

            while (logsIterator.hasNext()) {
                val resultsFromLog = logsIterator.next()
                val writer = factory.createXMLStreamWriter(this)

                try {
                    XMLXESOutputStream(writer).use {
                        it.write(resultsFromLog.toFlatSequence())
                    }
                    if (logsIterator.hasNext()) write(",".toByteArray())
                } finally {
                    writer.close()
                    flush()
                }
            }

            write("]}".toByteArray())
        }
    }

    fun queryDataSourceZIPXES(dataSourceId: UUID, query: String): OutputStream.() -> Unit {
        // All preparation must be done here rather than in the returned lambda, as the lambda will be invoked
        // when writing output stream and error messages (e.g., parse errors) cannot be returned through HTTP
        // from that stage of processing.
        val queryStream = createQueryStream(dataSourceId, query, downloadLimitFactor)

        return {
            ZipOutputStream(this, Charset.forName("utf-8")).use { zip ->
                zip.setLevel(Deflater.BEST_COMPRESSION)
                zip.setComment("File created using the ${Brand.name} software.")
                val factory = XMLOutputFactory.newInstance()

                for ((i, log) in queryStream.withIndex()) {
                    zip.putNextEntry(ZipEntry("$i.xes"))
                    val writer = factory.createXMLStreamWriter(zip, "utf-8")
                    try {
                        XMLXESOutputStream(writer).use {
                            it.write(log.toFlatSequence())
                        }
                    } finally {
                        writer.close()
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun createQueryStream(
        dataSourceId: UUID,
        query: String,
        limitFactor: Long = 1L
    ): DBHierarchicalXESInputStream {
        loggedScope { logger ->
            logger.info("Querying data source: $dataSourceId")
            logger.debug("User query: $query")

            val q = Query(query)
            q.applyLimits(logLimit, traceLimit * limitFactor, eventLimit * limitFactor)
            val queryStream = DBHierarchicalXESInputStream(dataSourceId.toString(), q)

            return queryStream
        }
    }
}
