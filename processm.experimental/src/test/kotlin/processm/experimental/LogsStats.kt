package processm.experimental

import org.junit.jupiter.api.Disabled
import processm.core.log.Event
import processm.core.log.Trace
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test

class LogsStats {

    @InMemoryXESProcessing
    @Test
    @Disabled("Not a test")
    fun computeStats() {
        val dir = "../xes-logs"
        val keywords = listOf("artificial", "synthetic", "benchmark")
        for (file in File(dir).listFiles()) {
            if (!file.name.endsWith(".xes.gz"))
                continue
            if (keywords.any { kw -> kw in file.name.toLowerCase() })
                continue
            file.inputStream().use { fileStream ->
                val stream = XMLXESInputStream(GZIPInputStream(fileStream))
                var nTraces = 0
                val names = HashSet<String>()
                for (element in stream) {
                    if (element is Trace)
                        nTraces++
                    if (element is Event)
                        names.add(element.conceptName.toString())
                }
                println("| ${file.name} | $nTraces | ${names.size} |")
            }
        }
    }
}