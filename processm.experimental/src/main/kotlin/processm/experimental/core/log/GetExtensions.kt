package processm.experimental.core.log

import processm.core.log.Extension
import processm.core.log.Log
import processm.core.log.XMLXESInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object GetExtensions {

    @JvmStatic
    fun main(args: Array<String>) {

        val executor = Executors.newFixedThreadPool(4)
        val extensions = ConcurrentHashMap<String, Extension>()

        println("Working directory: ${System.getProperty("user.dir")}")
        File("./xes-logs/").walk().forEach { file ->
            if (!file.canonicalPath.endsWith(".xes.gz"))
                return@forEach

            executor.submit {
                try {
                    file.inputStream().use {
                        GZIPInputStream(it, 16384).use { xes ->
                            for (log in XMLXESInputStream(xes).filterIsInstance<Log>().take(1)) {
                                extensions.putAll(log.extensions)
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("File ${file.name}: ${e::class.simpleName}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        executor.shutdown()
        executor.awaitTermination(1L, TimeUnit.DAYS)
        println("Extensions found:")
        println(extensions.entries.joinToString("\n") { (prefix, e) -> "$prefix:\t${e.uri}" })

        executor.shutdown()
    }
}
