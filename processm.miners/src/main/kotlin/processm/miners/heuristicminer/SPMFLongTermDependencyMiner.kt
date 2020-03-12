package processm.miners.heuristicminer

import org.apache.commons.collections4.bidimap.DualHashBidiMap
import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.avoidability.AvoidabilityChecker
import processm.miners.heuristicminer.avoidability.ValidSequenceBasedAvoidabilityChecker
import java.io.*
import java.nio.file.Files


class SPMFLongTermDependencyMiner(val isAvoidable: AvoidabilityChecker = ValidSequenceBasedAvoidabilityChecker()) :
    LongTermDependencyMiner {

    private val sequences = Files.createTempFile("input", ".txt")
    private val sequencesStream = PrintStream(FileOutputStream(sequences.toFile()))
    private val _node2int = DualHashBidiMap<Node, Int>()

    init {
        println("SEQUENCES FILE $sequences")
    }

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

    private val deps: List<Pair<Set<Node>, Set<Node>>> by lazy {
        runSequenceMining()
    }

    private fun runSequenceMining(): List<Pair<Set<Node>, Set<Node>>> {
        sequencesStream.flush()
        val results = Files.createTempFile("output", ".txt")
        val retcode = ProcessBuilder(
            "java",
            "-jar",
            "/home/smaug/praca/ProcessM/processm/spmf.jar",
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
                    Rule(pred, succ, sup, conf)
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
                    k.allSubsets().filter { it != k }.toList().flatMap { tmp.getOrDefault(it, setOf()) }.toSet()
                val vredundant = v.allSubsets().filter { it != v }.toList()
                    .flatMap { tmp.getOrDefault(it, setOf()) }.toSet()
                v - redundant - vredundant
            }
            .map { (pred, succ) -> pred.map { int2node(it) }.toSet() to succ.map { int2node(it) }.toSet() }
        println()
        println("DEPS")
        deps.forEach { (p, s) -> println("${p.map { it.activity }} -> ${s.map { it.activity }}") }
        return deps
    }

    override fun mine(currentModel: Model): Collection<Pair<Node, Node>> {
        isAvoidable.setContext(currentModel)
        val result = ArrayList<Pair<Node, Node>>()
        for ((pred, succ) in deps) {
            if (!isAvoidable(pred to succ))
                continue
            println("MINE ${pred.map { it.activity }} -> ${succ.map { it.activity }}")
            if (pred.size == 1 && succ.size == 1) {
                //1-to-1 dependency
                val r = pred.single() to succ.single()
                result.add(r)
            } else if (pred.size >= 2 && succ.size == 1) {
                //N-to-1 dependency
                val s = succ.single()
                val base = pred.map { it to s }
                result.addAll(base)
                result.addAll(pred.map { it to currentModel.end })
                result.add(s to currentModel.end)
            } else if (pred.size == 1 && succ.size >= 2) {
                //1-to-N dependency
                val p = pred.single()
                val base = succ.map { p to it }
                result.addAll(base)
                result.addAll(succ.map { currentModel.start to it })
                result.add(currentModel.start to p)
            } else {
                //N-to-M dependency
                //honestly, I'm not sure if this is sufficient
                result.addAll(succ.map { currentModel.start to it })
                result.addAll(pred.map { currentModel.start to it })
            }
        }
        result.forEach { println("FINAL ${it.first.activity} ${it.second.activity}") }
        return result
    }
}