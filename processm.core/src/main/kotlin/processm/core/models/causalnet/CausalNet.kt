package processm.core.models.causalnet

import com.carrotsearch.hppc.ObjectHashSet
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.MetadataHandler
import processm.helpers.asList
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * A read-only causal net model
 */
abstract class CausalNet(
    start: Node,
    end: Node,
    metadataHandler: MetadataHandler
) :
    ProcessModel,
    MetadataHandler by metadataHandler {

    companion object {
        private val setOfNull = setOf(null)
    }

    /**
     * A unique start activity instance, either real or artificial.
     *
     * If artificial, it is up to the user to populate [outgoing] and [incoming]
     */
    var start: Node = start
        protected set

    /**
     * A unique end activity instance, either real or artificial.
     *
     * If artificial, it is up to the user to populate [outgoing] and [incoming]
     */
    var end: Node = end
        protected set


    protected val _instances = HashSet(listOf(start, end))

    /**
     * Map from source to dependency
     */
    protected val _outgoing = HashMap<Node, HashSet<Dependency>>()

    /**
     * Map from target to dependency
     */
    protected val _incoming = HashMap<Node, HashSet<Dependency>>()
    protected val _splits = HashMap<Node, ArrayList<Split>>()
    protected val _joins = HashMap<Node, ArrayList<Join>>()


    /**
     * Nodes AKA instances of activities
     */
    val instances: Set<Node> = Collections.unmodifiableSet(_instances)

    /**
     * Convenience wrapper to retrieve all dependencies
     */
    val dependencies: Set<Dependency>
        get() = Collections.unmodifiableSet(_outgoing.values.flatten().toSet())

    /**
     * Outgoing arcs AKA what depends on a given node
     */
    val outgoing: Map<Node, Set<Dependency>>
        get() = Collections.unmodifiableMap(_outgoing)

    /**
     * Incoming arcs AKA what given node depends on
     */
    val incoming: Map<Node, Set<Dependency>>
        get() = Collections.unmodifiableMap(_incoming)

    /**
     * Splits AKA what other arcs must (not) be followed at the same time when going out of a node
     */
    val splits: Map<Node, List<Split>>
        get() = _splits

    /**
     * Joins AKA what other arcs must (not) be followed at the same time when going out of a node
     */
    val joins: Map<Node, List<Join>>
        get() = _joins

    /**
     * Same as [instances]
     */
    override val activities: List<Node>
        get() = instances.asList()

    /**
     * A single-element sequence consisting of [start]
     */
    override val startActivities: List<Node> = listOf(start)

    /**
     * A single-element sequence consisting of [end]
     */
    override val endActivities: List<Node> = listOf(end)

    /**
     * All decision points of the model. Each node (except [start] and [end]) generates two, one to chose a [Join] and the other to choose a [Split].
     * Some of them may be not real decisions, i.e., at most one possible outcome.
     */
    override val decisionPoints: Sequence<DecisionPoint>
        get() = splits.entries.asSequence().map { DecisionPoint(it.key, it.value, true) } +
                joins.entries.asSequence()
                    .map {
                        DecisionPoint(
                            it.key,
                            it.value,
                            false,
                            it.value.flatMapTo(HashSet()) { it.sources.asList() })
                    }

    override val controlStructures: Sequence<DecisionPoint>
        get() = decisionPoints

    @OptIn(ExperimentalContracts::class)
    private inline fun available(state: CausalNetState, callback: (node: Node, join: Join?, split: Split?) -> Unit) {
        contract {
            callsInPlace(callback)
        }

        if (state.isNotEmpty()) {
            val visitedNodes = ObjectHashSet<Node>(state.uniqueSize)
            for (dep in state.uniqueSet()) {
                val node = dep.target
                if (visitedNodes.add(node)) {
                    var joinIndex = 0
                    val joins = _joins[node].orEmpty()
                    //joinLoop@ for (join in _joins[node].orEmpty()) {
                    joinLoop@ while (joinIndex < joins.size) {
                        val join = joins[joinIndex++]
                        var index = 0
                        while (index < join.dependenciesAsArray.size) {
                            if (join.dependenciesAsArray[index++] !in state)
                                continue@joinLoop
                        }

//                        val splits = if (node != end) _splits[node].orEmpty() else setOfNull
//                        for (split in splits)
//                            callback(node, join, split)
                        if (node != end) {
                            val splits = _splits[node].orEmpty()
                            index = 0
                            while (index < splits.size) {
                                callback(node, join, splits[index++])
                            }
                        } else {
                            callback(node, join, null)
                        }
                    }
                }
            }
        } else if (state.isFresh /*prevent execution of activities in the final state*/) {
            for (split in _splits.getValue(start))
                callback(start, null, split)
        }
    }


    @Deprecated("Use available() instead")
    fun available4(state: CausalNetState, node: Node): List<DecoupledNodeExecution> {
        if (state.isNotEmpty()) {
            val relevant = state.uniqueSet().filterTo(HashSet()) { it.target == node }
            val result = ArrayList<DecoupledNodeExecution>()
            for (join in joins[node].orEmpty())
                if (relevant.containsAll(join.dependencies)) {
                    val splits = if (node != end) splits[node].orEmpty() else setOfNull
                    for (split in splits)
                        result.add(DecoupledNodeExecution(node, join, split))
                }
            return result
        } else
            if (node == start)
                return splits.getValue(start)
                    .map { split -> DecoupledNodeExecution(start, null, split) }
            else
                return emptyList()
    }

    /**
     * In the given [state], list of nodes that can be executed
     */
    fun availableNodes(state: CausalNetState): Set<Node> {
        return if (state.isNotEmpty()) {
            val flatState = HashMap<Node, MutableSet<Dependency>>()
            for (dep in state.uniqueSet())
                flatState.getOrPut(dep.target) { HashSet() }.add(dep)
            val result = HashSet<Node>()
            for ((node, deps) in flatState)
                if (joins[node]?.any { join -> deps.containsAll(join.dependencies) } == true)
                    result.add(node)
            return result
        } else
            setOf(start)
    }

    /**
     * In the given [state], list of nodes that can be executed, along with corresponding split and join
     */
    fun available(state: CausalNetState): Sequence<DecoupledNodeExecution> = sequence {
        available(state) { node, join, split ->
            yield(DecoupledNodeExecution(node, join, split))
        }
    }

    /**
     * A short-hand function for getting the indexth available execution. It is faster by an order of magnitude
     * than [available] when accessing only one execution. Do not use for accessing many executions.
     */
    internal fun available(state: CausalNetState, index: Int): DecoupledNodeExecution {
        var i = 0
        available(state) { node, join, split ->
            if (i++ == index)
                return DecoupledNodeExecution(node, join, split)
        }
        throw IndexOutOfBoundsException(index)
    }

    /**
     * Verifies whether the given [execution] is available in the given [state].
     */
    internal fun isAvailable(execution: DecoupledNodeExecution, state: CausalNetState): Boolean {
        if (state.isEmpty()) {
            val split = execution.split
            return execution.activity == start &&
                    execution.join === null &&
                    split !== null &&
                    _splits[start]!!.contains(split)
        }

        return state.uniqueSet().any { dep ->
            val node = dep.target
            val join = checkNotNull(execution.join)
            val split = execution.split
            execution.activity == node &&
                    _joins[node]!!.contains(join) &&
                    state.containsAll(join.dependencies) &&
                    ((node == end && split === null) || _splits[node]!!.contains(checkNotNull(split)))
        }
    }

    /**
     * Returns true if the given join is present in the model and false otherwise
     */
    operator fun contains(join: Join): Boolean {
        return _joins[join.target]?.contains(join) == true
    }

    /**
     * Returns true if the given split is present in the model and false otherwise
     */
    operator fun contains(split: Split): Boolean {
        return _splits[split.source]?.contains(split) == true
    }

    /**
     * True if the causal net contains [dependency]
     */
    operator fun contains(dependency: Dependency): Boolean =
        outgoing[dependency.source]?.contains(dependency) == true

    /**
     * A simplified textual representation of the model.
     *
     * Useful for debugging tests, not useful for displaying complete information to the user
     */
    override fun toString(): String = buildString {
        val model = this@CausalNet
        for (n in model.instances.sortedBy { it.activity }) {
            val i = model.incoming.getOrDefault(n, setOf()).map { dep -> dep.source }
            val j = model.joins.getOrDefault(n, setOf()).map { join -> join.sources.map { it } }
            val o = model.outgoing.getOrDefault(n, setOf()).map { dep -> dep.target }
            val s = model.splits.getOrDefault(n, setOf()).map { split -> split.targets.map { it } }
            append("$i/$j -> $n -> $o/$s\n")
        }
    }

    fun structurallyEquals(other: CausalNet): Boolean {
        return instances == other.instances &&
                incoming == other.incoming &&
                outgoing == other.outgoing &&
                splits == other.splits &&
                joins == other.joins
    }


    /**
     * True if [right] is isomorphic with [this], starting with [initial] as a (possibly empty) mapping from [this] to [right].
     */
    fun isomorphic(right: CausalNet, initial: Map<Node, Node>): Map<Node, Node>? =
        Isomorphism(this, right).run(initial)

}
