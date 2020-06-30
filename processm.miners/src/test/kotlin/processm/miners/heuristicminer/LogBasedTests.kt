package processm.miners.heuristicminer

import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.miners.heuristicminer.bindingproviders.BestFirstBindingProvider
import processm.miners.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.*

@InMemoryXESProcessing
class LogBasedTests {

    private fun load(logfile: String): Log {
        File(logfile).inputStream().use { base ->
            return HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(base))).first()
        }
    }

//    @Ignore
    @Test
    fun `bpi_challenge_2013_open_problems`() {
        val log=load("../xes-logs/bpi_challenge_2013_open_problems.xes.gz")
        val online = OnlineHeuristicMiner(bindingProvider = BestFirstBindingProvider(maxQueueSize = 100), longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        for(trace in log.traces) {
            println(trace.events.map { "${it.conceptName}/${it.lifecycleTransition}" }.toList())
            online.processTrace(trace)
            println(online.result)
        }
//        val offline = OfflineHeuristicMiner(bindingProvider = BestFirstBindingProvider(maxQueueSize = 100), longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
//        offline.processLog(log)
//        println(offline.result)
    }

    @Test
    fun `BPIC15_3`() {
        val log=load("../xes-logs/BPIC15_3.xes.gz")
        val offline = OfflineHeuristicMiner(bindingProvider = BestFirstBindingProvider(maxQueueSize = 100), longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        offline.processLog(log)
        println(offline.result)
    }

    @Test
    fun `BPIC15_2`() {
        val log=load("../xes-logs/BPIC15_2.xes.gz")
        val offline = OfflineHeuristicMiner(bindingProvider = BestFirstBindingProvider(maxQueueSize = 100), longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        offline.processLog(log)
        println(offline.result)
    }

    @Test
    fun `Receipt_phase_of_an_environmental_permit_application_process_WABO_CoSeLoG_project`() {
        val log=load("../xes-logs/Receipt_phase_of_an_environmental_permit_application_process_WABO_CoSeLoG_project.xes.gz")
        val offline = OfflineHeuristicMiner(bindingProvider = BestFirstBindingProvider(maxQueueSize = 100), longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        offline.processLog(log)
        println(offline.result)
    }

    @Test
    fun `bpi_challenge_2012`() {
        val log=load("../xes-logs/bpi_challenge_2012.xes.gz")
        val offline = OfflineHeuristicMiner(bindingProvider = BestFirstBindingProvider(maxQueueSize = 100), longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        offline.processLog(log)
        println(offline.result)
    }

}