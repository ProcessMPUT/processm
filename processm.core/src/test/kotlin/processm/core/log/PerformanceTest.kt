package processm.core.log

import org.openjdk.jol.info.GraphLayout
import processm.core.DBTestHelper
import processm.core.DBTestHelper.loadLog
import processm.core.querylanguage.Query
import java.io.ByteArrayInputStream
import java.io.File
import java.io.PrintStream
import java.util.zip.GZIPInputStream
import kotlin.system.measureTimeMillis
import kotlin.test.Ignore
import kotlin.test.Test

class PerformanceTest {

    data class Measurement(val timeMs: Double, val nRepeats: Int, val memBytes: Long)

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

    @Ignore("Not a real test")
    @Test
    fun test() {
        PrintStream("XES-performance-report.tsv").use { reportStream ->
            fun report(path: String, test: String, measurement: Measurement) {
                reportStream.println("$path\t$test\t${measurement.memBytes}\t${measurement.timeMs}\t${measurement.nRepeats}")
            }
            for (path in paths) {
                val data = ByteArrayInputStream(File(path).inputStream().use { raw ->
                    GZIPInputStream(raw).use { gzip ->
                        gzip.readAllBytes()
                    }
                })
                val loadToMem = measure(21) {
                    data.reset()
                    XMLXESInputStream(data).toList()
                }
                report(path, "loadToMem", loadToMem)
                data.reset()
                val uuid = loadLog(data)
                val loadFromDb = measure(6) {
                    DBXESInputStream(DBTestHelper.dbName, Query("where l:id=$uuid")).toList()
                }
                report(path, "loadFromDb", loadFromDb)
            }
        }
    }
}
