package processm.miners.heuristicminer

import processm.core.models.causalnet.*
import processm.core.models.causalnet.mock.Event
import java.util.*
import kotlin.collections.HashMap

typealias Trace = Sequence<Event>
typealias Log = Sequence<Trace>

internal infix fun <A, B> Collection<A>.times(right: Collection<B>): List<Pair<A, B>> {
    return this.flatMap { a -> right.map { b -> a to b } }
}

class HeuristicMiner(
    private val log: Log,
    val minDirectlyFollows: Int = 0,
    val minDependency: Double = 0.0,
    val minBindingSupport: Int = 0,
    val splitSelector: BindingSelector<Split> = CountSeparately(minBindingSupport),
    val joinSelector: BindingSelector<Join> = CountSeparately(minBindingSupport)
) {
    internal val directlyFollows: Map<Pair<Node, Node>, Int> by lazy {
        val result = HashMap<Pair<Node, Node>, Int>()
        log.forEach { trace ->
            val i = trace.iterator()
            var prev = Node(i.next().name)
            while (i.hasNext()) {
                val curr = Node(i.next().name)
                val key = prev to curr
                result[key] = result.getOrDefault(key, 0) + 1
                prev = curr
            }
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

    val result: MutableModel by lazy {
        val model = MutableModel()
        model.addInstance(*nodes.toTypedArray())
        (nodes times nodes)
            .filter { k -> directlyFollows.getOrDefault(k, 0) >= minDirectlyFollows }
            .filter { k -> dependency.getOrDefault(k, 0.0) >= minDependency }
            .forEach { (a, b) -> model.addDependency(a, b) }
        log
            .map { trace -> trace.map { e -> Node(e.name) }.toList() }
            .forEach { trace ->
                joinSelector.add(computeJoins(model, trace))
                splitSelector.add(computeSplits(model, trace))
            }
        joinSelector.best.forEach { join -> model.addJoin(join) }
        splitSelector.best.forEach { split -> model.addSplit(split) }

        repairStartAndEnd(model)

        model
    }
}