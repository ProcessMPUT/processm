package processm.services.logic

import de.odysseus.staxon.json.JsonXMLConfig
import de.odysseus.staxon.json.JsonXMLConfigBuilder
import de.odysseus.staxon.json.JsonXMLOutputFactory
import org.apache.commons.io.input.BoundedInputStream
import processm.core.Brand
import processm.core.communication.Producer
import processm.core.log.*
import processm.core.log.attribute.Attribute.IDENTITY_ID
import processm.core.log.attribute.toMutableAttributeMap
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.toFlatSequence
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.etl.helpers.notifyAboutNewData
import processm.helpers.getPropertyIgnoreCase
import processm.logging.loggedScope
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.stream.XMLOutputFactory

class LogsService(private val producer: Producer) {
    companion object {
        private const val xesFileInputSizeLimit = 5 * 1024 * 1024L
        private const val identityIdAttributeName = IDENTITY_ID
    }

    private val logLimit = getPropertyIgnoreCase("processm.logs.limit.log")?.toLongOrNull() ?: 10L
    private val traceLimit = getPropertyIgnoreCase("processm.logs.limit.trace")?.toLongOrNull() ?: 30L
    private val eventLimit = getPropertyIgnoreCase("processm.logs.limit.event")?.toLongOrNull() ?: 90L
    private val downloadLimitFactor =
        getPropertyIgnoreCase("processm.logs.limit.downloadLimitFactor")?.toLongOrNull() ?: 10L

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
                        .map {
                            val log = it as? Log ?: return@map it
                            val logAttributes = log.attributes.toMutableAttributeMap()

                            logAttributes.computeIfAbsent(identityIdAttributeName) { UUID.randomUUID() }

                            return@map Log(
                                logAttributes,
                                log.extensions.toMutableMap(),
                                log.traceGlobals.toMutableAttributeMap(),
                                log.eventGlobals.toMutableAttributeMap(),
                                log.traceClassifiers.toMutableMap(),
                                log.eventClassifiers.toMutableMap()
                            )
                        }
                )
            }
            // It is possible that the user first configures an object, and later uploads data relevant to the object
            notifyAboutNewData(dataStoreId)
        }
    }

    private fun processm.core.log.hierarchical.Log.toFilteredSequence(
        includeTraces: Boolean,
        includeEvents: Boolean
    ): XESInputStream {
        assert(!includeEvents || includeTraces) { "includeEvents implies includeTraces" }
        return when {
            includeEvents -> this.toFlatSequence()
            includeTraces -> sequenceOf(this) + this.traces
            else -> sequenceOf(this)
        }
    }

    /**
     * Executes the provided [query] against logs stored in [dataStoreId].
     *
     * @param includeEvents Whether to include events and traces in the response
     * @param includeTraces Whether to include traces in the response (valid only if includeEvents=false)
     */
    fun queryDataStoreJSON(
        dataStoreId: UUID,
        query: String,
        includeTraces: Boolean,
        includeEvents: Boolean
    ): OutputStream.() -> Unit {
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

            write("[".toByteArray())
            val logsIterator = queryStream.iterator()

            while (logsIterator.hasNext()) {
                val resultsFromLog = logsIterator.next()
                val writer = factory.createXMLStreamWriter(this)

                try {
                    XMLXESOutputStream(writer, true).use {
                        it.write(resultsFromLog.toFilteredSequence(includeTraces, includeEvents))
                    }
                } finally {
                    writer.close()
                }

                if (logsIterator.hasNext()) write(",".toByteArray())
            }

            write("]".toByteArray())
        }
    }

    /**
     * Executes the provided [query] against logs stored in [dataStoreId] and returns the result as zipped XES file.
     *
     * @param includeEvents Whether to include events and traces in the response
     * @param includeTraces Whether to include traces in the response (valid only if includeEvents=false)
     */
    fun queryDataStoreZIPXES(
        dataStoreId: UUID,
        query: String,
        includeTraces: Boolean,
        includeEvents: Boolean
    ): OutputStream.() -> Unit {
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
                            it.write(log.toFilteredSequence(includeTraces, includeEvents))
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
            logger.info("Querying data store $dataStoreId using user query $query")

            val q = Query(query)
            q.applyLimits(logLimit, traceLimit * limitFactor, eventLimit * limitFactor)
            val queryStream = DBHierarchicalXESInputStream(dataStoreId.toString(), q, readNestedAttributes)

            return queryStream
        }
    }
}
