package processm.services.logic

import de.odysseus.staxon.json.JsonXMLConfig
import de.odysseus.staxon.json.JsonXMLConfigBuilder
import de.odysseus.staxon.json.JsonXMLOutputFactory
import org.apache.commons.io.input.BoundedInputStream
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.Brand
import processm.core.communication.Producer
import processm.core.helpers.getPropertyIgnoreCase
import processm.core.log.*
import processm.core.log.attribute.Attribute.IDENTITY_ID
import processm.core.log.attribute.toMutableAttributeMap
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.toFlatSequence
import processm.core.logging.loggedScope
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.models.*
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
        private const val xesFileInputSizeLimit = 5_000_000L
        private val logLimit = getPropertyIgnoreCase("processm.logs.limit.log")?.toLongOrNull() ?: 10L
        private val traceLimit = getPropertyIgnoreCase("processm.logs.limit.trace")?.toLongOrNull() ?: 30L
        private val eventLimit = getPropertyIgnoreCase("processm.logs.limit.event")?.toLongOrNull() ?: 90L
        private val downloadLimitFactor =
            getPropertyIgnoreCase("processm.logs.limit.downloadLimitFactor")?.toLongOrNull() ?: 10L
        private const val identityIdAttributeName = IDENTITY_ID
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
                        .map {
                            val log = it as? Log ?: return@map it
                            val logAttributes = log.attributes.toMutableAttributeMap()

                            logAttributes.computeIfAbsent(identityIdAttributeName) {  UUID.randomUUID() }

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

            write("[".toByteArray())
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

            write("]".toByteArray())
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
     * Enqueues recreation of XES log based on data collected by [etlProcessId], stored in [dataStoreId].
     */
    fun enqueueXesExtractionFromMetaModel(dataStoreId: UUID, etlProcessId: UUID) {
        val dataStoreName = dataStoreId.toString()
        transaction(DBCache.get(dataStoreName).database) {
            val etlProcessDetails = EtlProcessesMetadata
                .innerJoin(DataConnectors)
                .slice(EtlProcessesMetadata.name, DataConnectors.dataModelId)
                .select { EtlProcessesMetadata.id eq etlProcessId }
                .firstOrNull() ?: throw ValidationException(
                Reason.ResourceNotFound,
                "The specified ETL process and/or data store does not exist"
            )
            val dataModelId = etlProcessDetails[DataConnectors.dataModelId]?.value ?: throw ValidationException(
                Reason.ResourceNotFound,
                "The specified ETL process and/or data store has no data model"
            )
            val etlProcessName = etlProcessDetails[EtlProcessesMetadata.name]

            producer.produce(ETL_PROCESS_CONVERSION_TOPIC) {
                setString(DATA_STORE_ID, "$dataStoreId")
                setString(ETL_PROCESS_ID, "$etlProcessId")
                setString(DATA_MODEL_ID, "$dataModelId")
                setString(ETL_PROCESS_NAME, etlProcessName)
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
