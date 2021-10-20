package processm.services.logic

import de.odysseus.staxon.json.JsonXMLConfig
import de.odysseus.staxon.json.JsonXMLConfigBuilder
import de.odysseus.staxon.json.JsonXMLOutputFactory
import org.apache.commons.io.input.BoundedInputStream
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.Brand
import processm.core.log.*
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.toFlatSequence
import processm.core.logging.loggedScope
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.models.DataStores
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
     * Stores the provided XES [logStream] in the specified [dataStoreId].
     */
    fun saveLogFile(dataStoreId: UUID, fileName: String?, logStream: InputStream) {
        loggedScope { logger ->
            logger.info("Saving new log file ($fileName) to $dataStoreId")
            DBXESOutputStream(
                DBCache.get(dataStoreId.toString()).getConnection()
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
     * Executes the provided [query] against logs stored in [dataStoreId].
     */
    fun queryDataStoreJSON(dataStoreId: UUID, query: String): OutputStream.() -> Unit {
        // All preparation must be done here rather than in the returned lambda, as the lambda will be invoked
        // when writing output stream and error messages (e.g., parse errors) cannot be returned through HTTP
        // from that stage of processing.
        val queryStream = createQueryStream(dataStoreId, query, false)

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
                    XMLXESOutputStream(writer, true).use {
                        it.write(resultsFromLog.toFlatSequence())
                    }
                } finally {
                    writer.close()
                }

                if (logsIterator.hasNext()) write(",".toByteArray())
            }

            write("]}".toByteArray())
        }
    }

    /**
     * Executes the provided [query] against logs stored in [dataStoreId] and returns the result as zipped XES file.
     */
    fun queryDataStoreZIPXES(dataStoreId: UUID, query: String): OutputStream.() -> Unit {
        // All preparation must be done here rather than in the returned lambda, as the lambda will be invoked
        // when writing output stream and error messages (e.g., parse errors) cannot be returned through HTTP
        // from that stage of processing.
        val queryStream = createQueryStream(dataStoreId, query, true, downloadLimitFactor)

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

    /**
     * Removes XES log specified by the [identityId] attribute value.
     */
    fun removeLog(dataStoreId: UUID, identityId: UUID): Unit {
        DBCache.get(dataStoreId.toString()).getConnection().use { connection ->
            DBLogCleaner.removeLog(connection, identityId)
        }
    }

    private fun createQueryStream(
        dataStoreId: UUID,
        query: String,
        readNestedAttributes: Boolean,
        limitFactor: Long = 1L,
    ): DBHierarchicalXESInputStream {
        loggedScope { logger ->
            logger.info("Querying data store: $dataStoreId")
            logger.debug("User query: $query")

            val q = Query(query)
            q.applyLimits(logLimit, traceLimit * limitFactor, eventLimit * limitFactor)
            val queryStream = DBHierarchicalXESInputStream(dataStoreId.toString(), q, readNestedAttributes)

            return queryStream
        }
    }
}
