// * Missing dependencies?
// * 1. Download ProM 6.11, install `Stream` and `Heuristic Miner` plugins
// * 2. Go to ~/.ProM611/packages
// * 3. for jar in */*.jar;do dir=`dirname $jar`; ver=`echo $dir | sed 's/^.*-//'`; package=`basename $jar .jar|tr '[:upper:]' '[:lower:]'`; mvn install:install-file -Dfile=$jar -DgroupId=prom -DartifactId=$package -Dversion=$ver -Dpackaging=jar;done

package processm.experimental.onlinehmpaper.prom

import org.deckfour.xes.classification.XEventNameClassifier
import org.deckfour.xes.id.XID
import org.deckfour.xes.model.XAttribute
import org.deckfour.xes.model.XAttributeMap
import org.deckfour.xes.model.impl.*
import org.processmining.models.cnet.CNet
import org.processmining.models.cnet.CNetNode
import org.processmining.plugins.heuristicsnet.miner.heuristics.miner.settings.HeuristicsMinerSettings
import org.processmining.stream.algorithms.*
import org.processmining.stream.config.algorithms.BudgetLossyCountingMinerConfiguration
import org.processmining.stream.config.algorithms.LossyCountingMinerConfiguration
import org.processmining.stream.config.algorithms.SpaceSavingMinerConfiguration
import org.processmining.stream.config.fragments.*
import org.processmining.stream.config.interfaces.MinerConfiguration
import org.processmining.stream.plugins.OnlineMinerPlugin
import processm.core.helpers.mapToSet
import processm.core.log.XMLXESInputStream
import processm.core.log.attribute.*
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.models.causalnet.*
import processm.experimental.onlinehmpaper.filterLog
import processm.miners.causalnet.onlineminer.AbstractOnlineMiner
import processm.miners.causalnet.onlineminer.OnlineMiner
import java.io.File
import java.util.*
import java.util.zip.GZIPInputStream

//
//////OnlineFileMinerPlugin expects that each line is a separate XML document, thus this class
////class Shinu: CLIOnlineFileMinerPlugin() {
////
////    override fun start(onlineAlgorithm: OnlineMiningAlgorithm) {
////        minerRunning = true
////        val configuration = onlineAlgorithm.configuration.getConfiguration(
////            FileInputConfiguration::class.java
////        ) as FileInputConfiguration?
////        if (configuration != null) {
////            miner = Thread {
////                try {
////                    FileInputStream(configuration.filename).use { gzipped ->
////                        GZIPInputStream(gzipped).use { stream ->
////                            stream.reader().use { reader ->
////                                println(converter.fromXML(reader.readText()) as XTrace)
////                            }
////                        }
////                    }
//////                    val fstream = FileInputStream(configuration.filename)
//////                    val `in` = DataInputStream(fstream)
//////                    val br = BufferedReader(InputStreamReader(`in`))
//////                    var streamLine: String?
//////                    var t: XTrace
//////                    while (minerRunning && br.readLine().also { streamLine = it } != null) {
//////                        t = converter.fromXML(streamLine) as XTrace
//////                        onlineAlgorithm.observe(t)
//////                        inc()
//////                    }
//////                    br.close()
//////                    `in`.close()
//////                    fstream.close()
////                } catch (e: IOException) {
////                    e.printStackTrace()
////                }
////                notifyFinish()
////            }
////            miner.start()
////        }
////    }
////}
//
//fun convert(inXesGz: String, outXesL: String) {
//    var eventCtr = 0
//    var traceCtr = 0
//    FileOutputStream(outXesL).use { output ->
//        PrintStream(output).use { printOutput ->
//            val converter = OSXMLConverter()
//            val log = XesXmlGZIPParser().parse(File(inXesGz)).single()
//            for (trace in log) {
//                traceCtr++
//                for (event in trace) {
//                    eventCtr++
//                    val auxTrace = XTraceImpl(trace.attributes)
//                    auxTrace.add(event.clone() as XEvent)
//                    val line = converter.toXML(auxTrace).replace("\n", "").replace("\r", "")
//                    printOutput.println(line)
//                }
//            }
//        }
//    }
//    println("$inXesGz: $traceCtr traces, $eventCtr events")
//}
//
//// TODO Nie jestem pewien, ale wyglada na to, ze xstream nie wie nic o XES, ale za to wie o xlog, xtrace, xevent czyli typach z OpenXES. To byłoby też zgodne z `(XTrace) converter.fromXML(streamLine)` w `OnlineFileMinerPlugin`
//// Czyli wychodzi, że trzeba wczytać gzipa za pomocą openxes i chyba zserializować do pliku po drodze dzieląc na eventy?
//fun convertold(inXesGz: String, outXesL: String) {
//    FileInputStream(inXesGz).use { gzipped ->
//        GZIPInputStream(gzipped).use { raw ->
//            val factory = XMLOutputFactory.newInstance()
//            var log: Log? = null
//            var trace: Trace? = null
//            FileOutputStream(outXesL).use { output ->
//                PrintStream(output).use { printOutput ->
//                    XMLXESInputStream(raw).forEach { component ->
//                        when (component) {
//                            is Log -> log = component
//                            is Trace -> trace = component
//                            is Event -> {
//                                checkNotNull(log)
//                                checkNotNull(trace)
//                                var line = String(ByteArrayOutputStream().use { intermediateOutputStream ->
//                                    XMLXESOutputStream(factory.createXMLStreamWriter(intermediateOutputStream)).use {
//                                        it.write(log!!)
//                                        it.write(trace!!)
//                                        it.write(component)
//                                    }
//                                    intermediateOutputStream.toByteArray()
//                                })
//                                check('\n' !in line)
//                                printOutput.println(line)
//                            }
//                            else -> throw Exception()
//                        }
//                    }
//                }
//            }
//        }
//    }
//}

fun CNet.toProcessMCausalNet(): MutableCausalNet {
    fun n2n(node: CNetNode): Node =
        Node(node.label, special = node.label in setOf("ARTIFICIAL_START", "ARTIFICIAL_END"))

    val cnet = MutableCausalNet(start = n2n(startNode), end = n2n(endNode))
    nodes.forEach { cnet.addInstance(n2n(it)) }
    inputBindings.forEach { binding ->
        val target = n2n(binding.node)
        val deps = binding.boundNodes.mapToSet { Dependency(n2n(it), target) }
        deps.forEach(cnet::addDependency)
        cnet.addJoin(Join(deps))
    }
    outputBindings.forEach { binding ->
        val source = n2n(binding.node)
        val deps = binding.boundNodes.mapToSet { Dependency(source, n2n(it)) }
        deps.forEach(cnet::addDependency)
        cnet.addSplit(Split(deps))
    }

    return cnet
}

//class MyOnlineMinerPlugin : OnlineMinerPlugin() {
//    override fun start(alg: OnlineMiningAlgorithm) {
//        val configuration = alg.configuration.getConfiguration(
//            FileInputConfiguration::class.java
//        ) as FileInputConfiguration
//        val log = XesXmlGZIPParser().parse(File(configuration.filename)).single()
//        for (trace in log) {
//            for (event in trace) {
//                val auxTrace = XTraceImpl(trace.attributes)
//                auxTrace.add(event.clone() as XEvent)
//                alg.observe(auxTrace)
//            }
//        }
//    }
//
//    override fun inc() {
//        TODO("Not yet implemented")
//    }
//
//    override fun notifyFinish() {
//        TODO("Not yet implemented")
//    }
//
//    override fun onModelUpdate(newModel: Any?) {
//        check(newModel is CNet)
//        println("Got new model: ${newModel.nodes.size} ${newModel.bindings.size}")
//        newModel.toProcessMCausalNet().toPM4PY(System.out)
//    }
//
//    override fun onNewFitnessValue(p0: Double) {
//        TODO("Not yet implemented")
//    }
//
//    override fun onNewPrecisionValue(p0: Double) {
//        TODO("Not yet implemented")
//    }
//
//    @Throws(OnlineException::class)
//    fun mine(algorithm: OnlineMiningAlgorithm, configuration: MinerConfiguration?) {
//        algorithm.prepareMiner(configuration)
//        start(algorithm)
//    }
//}

private fun <T> Attribute<T>.toXAttribute(): XAttribute =
    when (this) {
        is BoolAttr -> XAttributeBooleanImpl(this.key, this.value)
        is DateTimeAttr -> XAttributeTimestampImpl(this.key, Date.from(this.value))
        is IDAttr -> XAttributeIDImpl(this.key, XID(this.value))
        is IntAttr -> XAttributeDiscreteImpl(this.key, this.value)
        is ListAttr -> XAttributeListImpl(this.key).also { container ->
            this.value.forEach {
                container.addToCollection(
                    it.toXAttribute()
                )
            }
        }
        is NullAttr -> TODO("Dunno how to handle this")
        is RealAttr -> XAttributeContinuousImpl(this.key, this.value)
        is StringAttr -> XAttributeLiteralImpl(this.key, this.value)
        else -> throw UnsupportedOperationException("Class not implemented: ${this::class}")
    }

private fun Map<String, Attribute<*>>.toXAttributeMap(): XAttributeMap =
    mapValuesTo(XAttributeMapImpl()) { it.value.toXAttribute() }

enum class ShinuVariant {
    LOSSY_COUNTING,
    BUDGET_LOSSY_COUNTING,
    SPACE_SAVING
}

/**
 * [ShinuMiner] is a drop-in replacement for [OnlineMiner]
 */
class ShinuMiner(private val variant: ShinuVariant) : AbstractOnlineMiner, OnlineMinerPlugin() {

    private lateinit var model: MutableCausalNet
    private var traceCtr = 0
    private val alg: CNetBasedOnlineMiningAlgorithm

    init {
        val cfg: MinerConfiguration

        when (variant) {
            ShinuVariant.LOSSY_COUNTING -> {
                alg = LossyCountingHM(this)
                cfg = LossyCountingMinerConfiguration()
                with(LossyCountingConfiguration()) {
                    cfg.addConfiguration(this)
                }
            }
            ShinuVariant.BUDGET_LOSSY_COUNTING -> {
                alg = BudgetLossyCountingHM(this)
                cfg = BudgetLossyCountingMinerConfiguration()
                with(BudgetConfiguration()) {
                    cfg.addConfiguration(this)
                }
            }
            ShinuVariant.SPACE_SAVING -> {
                alg = SpaceSavingHM(this)
                cfg = SpaceSavingMinerConfiguration()
                with(BudgetConfiguration()) {
                    cfg.addConfiguration(this)
                }
            }
        }
        with(HeuristicsMinerConfiguration()) {
            settings = HeuristicsMinerSettings()
            settings.classifier = XEventNameClassifier()
            cfg.addConfiguration(this)
        }

        with(ModelMetricsConfiguration()) {
            modelUpdateFrequency = 1
            cfg.addConfiguration(this)
        }
        cfg.addConfiguration(FileInputConfiguration())
        alg.prepareMiner(cfg)
    }

    override fun processDiff(
        addLog: processm.core.log.hierarchical.Log,
        removeLog: processm.core.log.hierarchical.Log
    ) {
        // removeLog is ignored
        for (trace in addLog.traces) {
            traceCtr++
            for (event in trace.events) {
                val traceAttributes = trace.attributes.toXAttributeMap()
                //necessary to support calling Utils.getCaseID()
                traceAttributes.computeIfAbsent("concept:name") { XAttributeLiteralImpl("concept:name", "_:$traceCtr") }
                val auxTrace = XTraceImpl(traceAttributes)
                auxTrace.add(XEventImpl(event.attributes.toXAttributeMap()))
                alg.observe(auxTrace)
            }
        }
    }

    override fun processLog(log: processm.core.log.hierarchical.Log) = processDiff(
        log,
        processm.core.log.hierarchical.Log(emptySequence())
    )

    override val result: MutableCausalNet
        get() = model

    override fun start(p0: OnlineMiningAlgorithm?) {
        TODO("Not yet implemented")
    }

    override fun inc() {
        TODO("Not yet implemented")
    }

    override fun notifyFinish() {
        TODO("Not yet implemented")
    }

    override fun onModelUpdate(model: Any?) {
        check(model is CNet)
        this.model = model.toProcessMCausalNet()

    }

    override fun onNewFitnessValue(p0: Double) {
        TODO("Not yet implemented")
    }

    override fun onNewPrecisionValue(p0: Double) {
        TODO("Not yet implemented")
    }
}

@OptIn(InMemoryXESProcessing::class)
private fun load(logfile: File): processm.core.log.hierarchical.Log {
    logfile.inputStream().use { base ->
        return filterLog(HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(base))).first())
    }
}