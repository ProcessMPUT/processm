package processm.miners.heuristicminer.longdistance

import org.apache.commons.collections4.bidimap.DualHashBidiMap
import processm.core.helpers.allSubsets
import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.longdistance.avoidability.AvoidabilityChecker
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.FileReader
import java.io.PrintStream
import java.nio.file.Files

@RequiresOptIn(message = "This is a PoC kept to facilitate further development of long-distance dependency mining and not intended for production.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalLongDistanceDependencyMining

/**
 * An approach to long-distance dependency mining based on sequential rule mining.
 * To use this class, one must provide a implementation of sequential rule mining in
 * a form of an executable JAR file and with an interface compatible with those of SPMF
 * (https://www.philippe-fournier-viger.com/spmf/)
 */
@ExperimentalLongDistanceDependencyMining
private class SPMFLongDistanceDependencyMiner(
    val blackboxJarPath: String,
    isAvoidable: AvoidabilityChecker
) :
    AbstractAssociationsRulesLongDistanceDependencyMiner(isAvoidable) {

    private val sequences = Files.createTempFile("input", ".txt")
    private val sequencesStream = PrintStream(FileOutputStream(sequences.toFile()))
    private val _node2int = DualHashBidiMap<Node, Int>()

    private fun node2int(n: Node): Int {
        return _node2int.getOrPut(n, { _node2int.size + 1 })
    }

    private fun int2node(i: Int): Node {
        return _node2int.getKey(i)
    }

    override fun processTrace(trace: List<Node>) {
        val s = trace.joinToString(separator = " ") { node2int(it).toString() + " -1" } + " -2"
        sequencesStream.println(s)
    }

    private val lineParser = Regex("^(.*) ==> (.*) #SUP: (\\d+) #CONF: ([0-9.]+)$")

    private data class Rule(val pred: Set<Int>, val succ: Set<Int>, val support: Int, val confidence: Double)

    override val deps by lazy {
        runSequenceMining().map { LongTermDependency(it.first, it.second) }
    }

    private fun runSequenceMining(): List<Pair<Set<Node>, Set<Node>>> {
        sequencesStream.flush()
        val results = Files.createTempFile("output", ".txt")
        val retcode = ProcessBuilder(
            "java",
            "-jar",
            blackboxJarPath,
            "run",
            "CMRules",
            sequences.toAbsolutePath().toString(),
            results.toAbsolutePath().toString(),
            "1%",
            "100%"
        )
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()
        println("RETCODE $retcode")
        println(results)
        if (retcode != 0)
            throw IllegalStateException()
        val allRules = ArrayList<Rule>()
        BufferedReader(FileReader(results.toFile())).useLines { lines ->
            allRules.addAll(lines
                .map { line -> lineParser.matchEntire(line.trim()) }
                .map { m ->
                    if (m == null)
                        throw IllegalStateException()
                    val pred = m.groupValues[1].split(',').map { it.toInt() }.toSet()
                    val succ = m.groupValues[2].split(',').map { it.toInt() }.toSet()
                    val sup = m.groupValues[3].toInt()
                    val conf = m.groupValues[4].toDouble()
                    Rule(
                        pred,
                        succ,
                        sup,
                        conf
                    )
                })
        }
        allRules.forEach { rule -> println("${rule.pred.map { int2node(it).activity }} -> ${rule.succ.map { int2node(it).activity }}") }

        val toRemove = HashSet<Int>()
        for (i in 0 until allRules.size) {
            val rule = allRules[i]
//            if (toRemove.contains(i))
//                continue
            for (j in i + 1 until allRules.size) {
                if (toRemove.contains(j))
                    continue
                val other = allRules[j]
                if (other.pred.containsAll(rule.pred) && rule.succ.containsAll(other.succ))
                    toRemove.add(j)
            }
        }

        val rules = allRules.filterIndexed { idx, rule -> !toRemove.contains(idx) }
        println()
        println("PRUNED")
        rules.forEach { rule -> println("${rule.pred.map { int2node(it).activity }} -> ${rule.succ.map { int2node(it).activity }}") }

        var tmp = rules
            .groupBy { it.pred }
            .mapValues { (k, v) -> v.map { it.succ } }
            .mapValues { (k, v) ->
                val s = v.map { it.size }.max()!!
                val x = v.filter { it.size == s }
                assert(x.size == 1)
                x.single()
//                var tmp=v
//                for(s in 1 until v.map { it.size }.max()!!) {
//                    val single = tmp.filter { it.size <= s }.flatten()
//                    tmp = tmp.filter { it.size <= s || !single.containsAll(it) }
//                }
//                tmp
            }
        println()
        println("PRUNNED EVEN MORE")
        tmp.forEach { (p, s) -> println("${p.map { int2node(it).activity }} -> ${s.map { int2node(it).activity }}") }
        val deps = tmp
            .mapValues { (k, v) ->
                val redundant =
                    k.allSubsets().map { it.toSet() }.filter { it != k }.toList()
                        .flatMap { tmp.getOrDefault(it, setOf()) }.toSet()
                val vredundant = v.allSubsets().map { it.toSet() }.filter { it != v }.toList()
                    .flatMap { tmp.getOrDefault(it, setOf()) }.toSet()
                v - redundant - vredundant
            }
            .map { (pred, succ) -> pred.map { int2node(it) }.toSet() to succ.map { int2node(it) }.toSet() }
        println()
        println("DEPS")
        deps.forEach { (p, s) -> println("${p.map { it.activity }} -> ${s.map { it.activity }}") }
        return deps
    }

}