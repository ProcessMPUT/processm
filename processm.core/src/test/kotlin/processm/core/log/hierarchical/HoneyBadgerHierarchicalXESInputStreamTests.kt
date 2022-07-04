package processm.core.log.hierarchical

import org.openjdk.jol.info.GraphLayout
import processm.core.helpers.hierarchicalCompare
import processm.core.log.XMLXESInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(InMemoryXESProcessing::class)
class HoneyBadgerHierarchicalXESInputStreamTests {
    @Test
    fun `Sepsis`() {
        val file = File("../xes-logs/Sepsis_Cases-Event_Log.xes.gz")

        // warm-up and check for functional equivalence
        run {
            val honeyBadgerOld = readHBO(file)
            val honeyBadger = readHB(file)
            assertTrue(hierarchicalCompare(honeyBadgerOld, honeyBadger))

            // measure memory usage
            val hboMem = GraphLayout.parseInstance(honeyBadgerOld)
            val hbMem = GraphLayout.parseInstance(honeyBadger)

            println("HBold size:\n${hboMem.toFootprint()}")
            println("HB size:\n${hbMem.toFootprint()}")

            assertTrue(hboMem.totalSize() > hbMem.totalSize())
        }

        System.gc()

        // measure time
        val rep = 5
        val hboTime = measureTimeMillis { repeat(rep) { readHBO(file) } } / rep
        val hbTime = measureTimeMillis { repeat(rep) { readHB(file) } } / rep
        println("HBold time:  $hboTime ms\nHB time: $hbTime ms")
    }

    @Suppress("DEPRECATION")
    private fun readHBO(file: File): HoneyBadgerHierarchicalXESInputStreamOld =
        file.inputStream().use { input ->
            GZIPInputStream(input).use { gzip ->
                HoneyBadgerHierarchicalXESInputStreamOld(XMLXESInputStream(gzip)).also { it.first() /* prevent lazy read after stream is closed */ }
            }
        }

    private fun readHB(file: File): HoneyBadgerHierarchicalXESInputStream =
        file.inputStream().use { input ->
            GZIPInputStream(input).use { gzip ->
                HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(gzip)).also { it.first() /* prevent lazy read after stream is closed */ }
            }
        }
}
