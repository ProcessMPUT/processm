package processm.experimental.core.log

import processm.core.log.Event
import processm.core.log.Log
import processm.core.log.XMLXESInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object GetLifecycleModel {

    @JvmStatic
    fun main(args: Array<String>) {

        val executor = Executors.newFixedThreadPool(4)
        val lifecycleModels = ConcurrentHashMap<String, Int>()
        val lifecycleStates = ConcurrentHashMap<String, Int>()
        val lifecycleTransitions = ConcurrentHashMap<String, Int>()

        println("Working directory: ${System.getProperty("user.dir")}")
        File("./xes-logs/").walk().forEach { file ->
            if (!file.canonicalPath.endsWith(".xes.gz"))
                return@forEach

            executor.submit {
                try {
                    file.inputStream().use {
                        GZIPInputStream(it, 16384).use { xes ->
                            for (component in XMLXESInputStream(xes)) {
                                when (component) {
                                    is Log -> lifecycleModels.compute(component.lifecycleModel ?: "") { _, old ->
                                        (old ?: 0) + 1
                                    }
                                    is Event -> {
                                        lifecycleStates.compute(component.lifecycleState ?: "") { _, old ->
                                            (old ?: 0) + 1
                                        }
                                        lifecycleTransitions.compute(component.lifecycleTransition ?: "") { _, old ->
                                            (old ?: 0) + 1
                                        }
                                    }
                                }
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

        println("Lifecycle models found:")
        println(lifecycleModels.entries.joinToString("\n") { (model, count) -> "$model:\t$count" })

        println("Lifecycle states found:")
        println(lifecycleStates.entries.joinToString("\n") { (state, count) -> "$state:\t$count" })

        println("Lifecycle transition found:")
        println(lifecycleTransitions.entries.joinToString("\n") { (transition, count) -> "$transition:\t$count" })
    }
}
