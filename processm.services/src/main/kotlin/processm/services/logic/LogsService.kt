package processm.services.logic

import de.odysseus.staxon.json.JsonXMLConfig
import de.odysseus.staxon.json.JsonXMLConfigBuilder
import de.odysseus.staxon.json.JsonXMLOutputFactory
import org.apache.commons.io.input.BoundedInputStream
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
import java.util.*
import java.util.zip.GZIPInputStream

class LogsService {
    private val xesFileInputSizeLimit = 5_000_000L

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
    fun queryDataSource(dataSourceId: UUID, query: String): OutputStream.() -> Unit {
        loggedScope { logger ->
            logger.info("Querying data source: $dataSourceId")
            logger.debug("User query: $query")

            val queryStream = DBHierarchicalXESInputStream(dataSourceId.toString(), Query(query))
            val config: JsonXMLConfig =
                JsonXMLConfigBuilder()
                    .autoArray(true).autoPrimitive(true).prettyPrint(true).build()
            return {
                write("{\"${QueryResultCollectionMessageBody::data.name}\":[".toByteArray())
                val logsIterator = queryStream.iterator()

                while (logsIterator.hasNext()) {
                    val resultsFromLog = logsIterator.next()
                    val writer = JsonXMLOutputFactory(config).createXMLStreamWriter(this)

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
    }
}