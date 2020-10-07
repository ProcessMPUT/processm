package processm.experimental.onlinehmpaper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import processm.core.log.XESInputStream
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.models.causalnet.CausalNet
import processm.miners.heuristicminer.OfflineHeuristicMiner
import processm.miners.heuristicminer.OnlineHeuristicMiner
import processm.miners.heuristicminer.dependencygraphproviders.DefaultDependencyGraphProvider
import processm.miners.heuristicminer.dependencygraphproviders.L2DependencyGraphProvider
import processm.miners.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import java.io.File

@Serializable
private data class DanielNode(val id:String, val splits: List<List<String>>, val joins: List<List<String>>)

@Serializable
private data class DanielEdge(val sourceNodeId:String, val targetNodeId:String)

@Serializable
private data class DanielCNet(val nodes:List<DanielNode>, val edges:List<DanielEdge>, val query: String="SELECT ...")

@Serializable
private data class DanielCNetWrapper(val name:String, val data: DanielCNet) {
    val id="cf607cb0-0b88-4ccd-9795-5cd0201b3c39"
    val type="causalNet"
}

fun CausalNet.toDanielJS(name: String):String {
    val nodes =
        (sequenceOf(start) +
        this.activities.filter { it != start && it != end } +
                sequenceOf(end))
            .map { DanielNode(
                it.name,
                this.splits[it].orEmpty().map { split -> split.targets.map { t-> t.name } },
                this.joins[it].orEmpty().map { join -> join.sources.map { s -> s.name } }
            ) }
            .toList()
    val edges = this.dependencies.map { DanielEdge(it.source.name, it.target.name) }
    val json = Json(JsonConfiguration.Default)
    return json.stringify(DanielCNetWrapper.serializer(), DanielCNetWrapper(name, DanielCNet(nodes, edges)))
}

@ExperimentalStdlibApi
@InMemoryXESProcessing
fun main(args: Array<String>) {
    val log = File("processm.core/src/test/resources/xes-logs/JournalReview.xes").inputStream().use {
        HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(it)).first()
    }
    val hm=OfflineHeuristicMiner(dependencyGraphProvider = L2DependencyGraphProvider(1, .5, .5),
        longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
    )
    hm.processLog(log)
    println(hm.result)
    println(hm.result.toDanielJS("journal"))
}
