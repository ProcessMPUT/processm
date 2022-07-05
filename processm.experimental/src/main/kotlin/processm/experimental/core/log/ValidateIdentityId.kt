package processm.experimental.core.log

import processm.core.log.attribute.Attribute.Companion.IDENTITY_ID
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

object ValidateIdentityId {

    @JvmStatic
    fun main(args: Array<String>) {

        val executor = Executors.newFixedThreadPool(4)
        val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance()

        println("Working directory: ${System.getProperty("user.dir")}")
        File("./xes-logs/").walk().forEach { file ->
            if (!file.canonicalPath.endsWith(".xes.gz"))
                return@forEach

            executor.submit {
                file.inputStream().use {
                    GZIPInputStream(it, 16384).use { xes ->
                        var totalCount = 0
                        var parsableCount = 0
                        val reader = xmlInputFactory.createXMLStreamReader(xes)
                        while (reader.next() != XMLStreamConstants.END_DOCUMENT) {
                            if (!reader.isStartElement ||
                                (reader.name.localPart != "id" &&
                                        (reader.name.localPart != "string" ||
                                                IDENTITY_ID != reader.getAttributeValue(null, "key")))
                            )
                                continue

                            val uuidString = reader.getAttributeValue(null, "value")
                            try {
                                ++totalCount
                                UUID.fromString(uuidString)
                                ++parsableCount
                            } catch (e: Exception) {
                                print("${file.name}: ")
                                e.printStackTrace()
                            }
                        }

                        if (totalCount > 0L)
                            println("${file.name}: $parsableCount parsable ids out of $totalCount ids.")
                    }
                }
            }
        }

        executor.shutdown()
    }
}
