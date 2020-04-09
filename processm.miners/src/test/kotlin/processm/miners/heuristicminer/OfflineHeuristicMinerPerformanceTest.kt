package processm.miners.heuristicminer

import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.core.verifiers.causalnet.CausalNetVerifierImpl
import processm.miners.heuristicminer.bindingproviders.BestFirstBindingProvider
import processm.miners.heuristicminer.dependencygraphproviders.BasicDependencyGraphProvider
import processm.miners.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Ignore
import kotlin.test.assertTrue

@InMemoryXESProcessing
class OfflineHeuristicMinerPerformanceTest {

    @InMemoryXESProcessing
    private fun extractLog(file: File): Log {
        file.absoluteFile.inputStream().use { fileStream ->
            GZIPInputStream(fileStream).use { stream ->
                return@extractLog HoneyBadgerHierarchicalXESInputStream(
                    XMLXESInputStream(stream)
                ).single()
            }
        }
    }

    @Ignore("This is not a real test and consumes a lot of time. Also, some of the subtests currently fail due to their size.")
    @TestFactory
    fun factory(): Iterator<DynamicNode> {
        return File("../xes-logs/")
            .walk()
            .filter { file -> file.canonicalPath.endsWith(".xes.gz") }
            .sortedBy { file -> file.name.toLowerCase() }
            .map { file ->
                DynamicTest.dynamicTest(file.name, file.toURI()) {
                    val log = extractLog(file)
                    val hm = OfflineHeuristicMiner(
                        dependencyGraphProvider = BasicDependencyGraphProvider(1),
                        bindingProvider = BestFirstBindingProvider(),
                        longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
                    )
                    hm.processLog(log)
                    println(hm.result)
                    //only this little verification, because soundness verification OOMs
                    val v = CausalNetVerifierImpl(hm.result)
                    assertTrue(v.isConnected)
                    assertTrue(v.allDependenciesUsed())
                }
            }.iterator()
    }

}