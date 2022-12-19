package processm.core.log

import org.openjdk.jol.info.GraphLayout
import processm.core.DBTestHelper
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import java.io.ByteArrayInputStream
import java.io.File
import java.io.PrintStream
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.system.measureTimeMillis
import kotlin.test.Ignore
import kotlin.test.Test

class PerformanceTest {

    data class Measurement(val timeMs: Double, val nRepeats: Int, val memBytes: Long)

    private fun <T> measureNoRestart(repeats: Int = 1, block: (Int) -> T): Measurement {
        val time = measureTimeMillis { repeat(repeats) { block(it) } }.toDouble() / repeats
        return Measurement(time, repeats, 0)
    }

    private fun <T> measure(repeats: Int, block: (Int) -> T): Measurement {
        val mem = GraphLayout.parseInstance(block(0))
        System.gc()
        val time = if (repeats > 1)
            measureTimeMillis { repeat(repeats - 1) { block(it + 1) } }.toDouble() / (repeats - 1)
        else
            Double.NaN
        return Measurement(time, repeats - 1, mem.totalSize())
    }

    // zgrep -lE '<(string|int|float)[^<>]*[^/]>' *.gz
    val paths = """BPIC12.xes.gz
BPIC13_cp.xes.gz
BPIC13_i.xes.gz
BPIC15_1.xes.gz
BPIC15_2.xes.gz
BPIC15_3.xes.gz
BPIC15_4.xes.gz
BPIC15_5.xes.gz
bpi_challenge_2012.xes.gz
bpi_challenge_2013_closed_problems.xes.gz
bpi_Challenge_2013_incidents.xes.gz
bpi_challenge_2013_open_problems.xes.gz
bpi_challenge_2017.xes.gz
bpi_challenge_2019.xes.gz
CoSeLoG_WABO_1.xes.gz
CoSeLoG_WABO_2.xes.gz
CoSeLoG_WABO_3.xes.gz
CoSeLoG_WABO_4.xes.gz
CoSeLoG_WABO_5.xes.gz
Hospital_Billing-Event_Log.xes.gz
Hospital_log.xes.gz
JUnit_4.12_software_event_log.xes.gz
junit.xes.gz
nasa-cev-1-10-splitted.xes.gz
nasa-cev-complete-splitted.xes.gz
Receipt_phase_of_an_environmental_permit_application_process_WABO_CoSeLoG_project.xes.gz
Road_Traffic_Fine_Management_Process.xes.gz
RTFMP.xes.gz
Sepsis_Cases-Event_Log.xes.gz
Statechart_Workbench_and_Alignments_Software_Event_Log.xes.gz
Synthetic_Event_Logs-Loan_application_example-ETM_Configuration1.xes.gz
Synthetic_Event_Logs-Loan_application_example-ETM_Configuration2.xes.gz
Synthetic_Event_Logs-Loan_application_example-ETM_Configuration3.xes.gz
Synthetic_Event_Logs-Loan_application_example-ETM_Configuration4.xes.gz""".split("\n").map { "../xes-logs/$it" }

    private fun gitTag(): String? {
        try {
            with(ProcessBuilder("git", "describe", "--always", "--dirty")) {
                redirectOutput()
                val proc = start()
                if (proc.waitFor(5, TimeUnit.SECONDS))
                    return proc.inputStream.bufferedReader().readLine().trim()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    @Ignore("Not a real test")
    @Test
    fun test() {
        DBCache.get(DBTestHelper.dbName).getConnection().use {
            // Do nothing, this is just to init the DB
        }
        val maxNoOfComponentsToWriteToDb = 1000 // 2 * Short.MAX_VALUE.toInt()
        val tag = gitTag()
        PrintStream("XES-performance-report-$tag.tsv").use { reportStream ->
            fun report(path: String, test: String, measurement: Measurement) {
                println("$path\t$test\t${measurement.memBytes}\t${measurement.timeMs}\t${measurement.nRepeats}")
                System.out.flush()
                reportStream.println("$path\t$test\t${measurement.memBytes}\t${measurement.timeMs}\t${measurement.nRepeats}")
                reportStream.flush()
            }
            for (path in paths) {
                val data = ByteArrayInputStream(File(path).inputStream().use { raw ->
                    GZIPInputStream(raw).use { gzip ->
                        gzip.readAllBytes()
                    }
                })
                var components: List<XESComponent> = emptyList()
                val loadToMem = measure(11) {
                    data.reset()
                    components = XMLXESInputStream(data).toList()
                    return@measure components
                }
                if (components.size > maxNoOfComponentsToWriteToDb)
                    components = components.subList(0, maxNoOfComponentsToWriteToDb)
                report(path, "loadToMem", loadToMem)
                val uuids = ArrayList<UUID>()
                val saveToDb = measureNoRestart(1) {
                    DBXESOutputStream(DBCache.get(DBTestHelper.dbName).getConnection()).use { output ->
                        val uuid = UUID.randomUUID()

                        output.write(components.asSequence().map {
                            if (it is processm.core.log.Log) /* The base class for log */
                                it.identityId = uuid
                            it
                        })
                        uuids.add(uuid)
                    }
                }
                report(path, "saveToDb", saveToDb)
                val loadFromDb = measureNoRestart(uuids.size) {
                    val uuid = uuids[it.coerceAtMost(uuids.size - 1)]
                    DBXESInputStream(DBTestHelper.dbName, Query("where l:id=$uuid")).toList()
                }
                report(path, "loadFromDb", loadFromDb)
            }
        }
    }
}
