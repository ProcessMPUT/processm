package processm.miners.heuristicminer

import processm.core.models.causalnet.*
import processm.core.models.causalnet.mock.Event
import processm.core.models.causalnet.verifier.ActivityBinding
import processm.core.models.causalnet.verifier.CausalNetSequence
import processm.core.models.causalnet.verifier.Verifier
import java.util.*
import kotlin.collections.HashMap

typealias Trace = Sequence<Event>
typealias Log = Sequence<Trace>

internal infix fun <A, B> Collection<A>.times(right: Collection<B>): List<Pair<A, B>> {
    return this.flatMap { a -> right.map { b -> a to b } }
}

class HeuristicMiner(
    private val log: Log,
    val minDirectlyFollows: Int = 1,
    val minDependency: Double = 1e-10,
    val minBindingSupport: Int = 1,
    val longTermDependencyMiner: LongTermDependencyMiner = NaiveLongTermDependencyMiner(),
    val splitSelector: BindingSelector<Split> = CountSeparately(minBindingSupport),
    val joinSelector: BindingSelector<Join> = CountSeparately(minBindingSupport)
) {
    internal val directlyFollows: Counter<Pair<Node, Node>> by lazy {
        val result = Counter<Pair<Node, Node>>()
        log.forEach { trace ->
            println("TRACE: " + trace.map { e -> e.name }.toList())
            val i = trace.iterator()
            var prev = start
            while (i.hasNext()) {
                val curr = Node(i.next().name)
                result.inc(prev to curr)
                prev = curr
            }
            result.inc(prev to end)
        }
        result
    }

    internal val nodes: Set<Node> by lazy {
        (directlyFollows.keys.map { it.first } + directlyFollows.keys.map { it.second }).distinct().toSet()
    }

    internal val dependency: Map<Pair<Node, Node>, Double> by lazy {
        (nodes times nodes).associateWith { (a, b) ->
            if (a != b) {
                val ab = directlyFollows.getOrDefault(a to b, 0)
                val ba = directlyFollows.getOrDefault(b to a, 0)
                (ab - ba) / (ab + ba + 1.0)
            } else {
                val aa = directlyFollows.getOrDefault(a to a, 0)
                aa / (aa + 1.0)
            }
        }
    }

    private fun computeSplits(model: Model, trace: List<Node>): List<Split> {
        val consequences = trace
            .mapIndexed { idx, node ->
                val after = trace.subList(idx + 1, trace.size)
                model.outgoing.getOrDefault(node, setOf())
                    .filter { dep -> after.contains(dep.target) }
                    .map { dep -> idx + 1 + after.indexOf(dep.target) }
                    .sorted()
            }
        return consequences
            .map { sucessors ->
                val remove = HashSet<Int>()
                sucessors
                    .flatMap { p ->
                        if (!remove.contains(p)) {
                            remove.addAll(consequences[p])
                            setOf(p)
                        } else {
                            setOf()
                        }
                    }
                    .toSet()
            }
            .mapIndexed { idx, sucessors ->
                sucessors.map { s -> Dependency(trace[idx], trace[s]) }
            }
            .filter { deps -> deps.isNotEmpty() }
            .map { deps ->
                Split(deps.toSet())
            }
    }

    private fun computeJoins(model: Model, trace: List<Node>): List<Join> {
        val causes = trace
            .mapIndexed { idx, node ->
                val before = trace.subList(0, idx)
                model.incoming.getOrDefault(node, setOf())
                    .filter { dep -> before.contains(dep.source) }
                    .map { dep -> before.lastIndexOf(dep.source) }
                    .sorted()
                    .asReversed()
            }
        // sprzatanie w incoming
        // Postepujac od konca, odejmuj incoming elementow, ktore ciagle sa w tym incomingu, w kolejnosci logu
        // Nastepnie dodaj wszystkie elementy, ktorych incoming odjales
        return causes
            .asReversed()
            .map { predecessors ->
                val remove = HashSet<Int>()
                predecessors
                    .flatMap { p ->
                        if (!remove.contains(p)) {
                            remove.addAll(causes[p])
                            setOf(p)
                        } else {
                            setOf()
                        }
                    }
                    .toSet()
            }.asReversed()
            .mapIndexed { idx, predecessors ->
                predecessors.map { p -> Dependency(trace[p], trace[idx]) }
            }
            .filter { deps -> deps.isNotEmpty() }
            .map { deps -> Join(deps.toSet()) }
    }

    private fun repairStartAndEnd(model: MutableModel) {
        model.instances
            .filter { n -> !n.special }
            .filter { n -> model.incoming[n].isNullOrEmpty() }
            .forEach { realStart ->
                val dep = model.addDependency(model.start, realStart)
                model.addSplit(Split(setOf(dep)))
                model.addJoin(Join(setOf(dep)))
            }

        model.instances
            .filter { n -> !n.special }
            .filter { n -> model.outgoing[n].isNullOrEmpty() }
            .forEach { realEnd ->
                val dep = model.addDependency(realEnd, model.end)
                model.addSplit(Split(setOf(dep)))
                model.addJoin(Join(setOf(dep)))
            }
    }

    internal val start = Node("start", special = true)
    internal val end = Node("end", special = true)

    val result: MutableModel by lazy {
        val model = MutableModel(start = start, end = end)
        model.addInstance(*nodes.toTypedArray())
        directlyFollows
            .filterValues { it >= minDirectlyFollows }
            .keys
            .filter { k -> dependency.getOrDefault(k, 0.0) >= minDependency }
            .forEach { (a, b) -> model.addDependency(a, b) }
        log
            .map { trace ->
                longTermDependencyMiner.processTrace(trace)
                trace
            }
            .map { trace -> listOf(start) + trace.map { e -> Node(e.name) }.toList() + listOf(end) }
            .forEach { trace ->
                joinSelector.add(computeJoins(model, trace))
                splitSelector.add(computeSplits(model, trace))
            }
        joinSelector.best.forEach { join -> model.addJoin(join) }
        splitSelector.best.forEach { split -> model.addSplit(split) }
        repairStartAndEnd(model)
        while (true) {
            val ltdeps = longTermDependencyMiner.mine(model)
            if (ltdeps.isNotEmpty()) {
                ltdeps.forEach { dep ->
                    val dep = model.addDependency(dep.first, dep.second)
                    model.splits.values.flatten().forEach { split ->
                        if (split.source == dep.source) {
                            model.removeSplit(split)
                            model.addSplit(Split(split.dependencies + setOf(dep)))
                        }
                    }
                    model.joins.values.flatten().forEach { join ->
                        if (join.target == dep.target) {
                            model.removeJoin(join)
                            model.addJoin(Join(join.dependencies + setOf(dep)))
                        }
                    }
                }
            } else
                break
        }

        model
    }
}