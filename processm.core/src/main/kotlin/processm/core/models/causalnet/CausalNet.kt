package processm.core.models.causalnet

import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.MetadataHandler
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * A read-only causal net model
 */
abstract class CausalNet(
    /**
     * A unique start activity instance, either real or artificial.
     *
     * If artificial, it is up to the user to populate [outgoing] and [incoming]
     */
    val start: Node,
    /**
     * A unique end activity instance, either real or artificial.
     *
     * If artificial, it is up to the user to populate [outgoing] and [incoming]
     */
    val end: Node,
    metadataHandler: MetadataHandler
) :
    ProcessModel,
    MetadataHandler by metadataHandler {
    protected val _instances = HashSet(listOf(start, end))

    /**
     * Map from source to dependency
     */
    protected val _outgoing = HashMap<Node, HashSet<Dependency>>()

    /**
     * Map from target to dependency
     */
    protected val _incoming = HashMap<Node, HashSet<Dependency>>()
    protected val _splits = HashMap<Node, HashSet<Split>>()
    protected val _joins = HashMap<Node, HashSet<Join>>()


    /**
     * Nodes AKA instances of activities
     */
    val instances: Set<Node> = Collections.unmodifiableSet(_instances)

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
    val splits: Map<Node, Set<Split>>
        get() = Collections.unmodifiableMap(_splits)

    /**
     * Joins AKA what other arcs must (not) be followed at the same time when going out of a node
     */
    val joins: Map<Node, Set<Join>>
        get() = Collections.unmodifiableMap(_joins)

    /**
     * Same as [instances]
     */
    override val activities: Sequence<Node>
        get() = instances.asSequence()

    /**
     * A single-element sequence consisting of [start]
     */
    override val startActivities: Sequence<Node> = sequenceOf(start)

    /**
     * A single-element sequence consisting of [end]
     */
    override val endActivities: Sequence<Node> = sequenceOf(end)

    /**
     * All decision points of the model. Each node (except [start] and [end]) generates two, one to chose a [Join] and the other to chose a [Split].
     * Some of them may be not real decisions, i.e., at most one possible outcome.
     */
    override val decisionPoints: Sequence<DecisionPoint>
        get() = splits.entries.asSequence().map { DecisionPoint(it.key, it.value) } +
                joins.entries.asSequence().map { DecisionPoint(it.key, it.value) }

    /**
     * In the given [state], list of nodes that can be executed, along with corresponding split and join
     */
    internal fun available(state: CausalNetState): Sequence<DecoupledNodeExecution> = sequence {
        if (state.isNotEmpty()) {
            for (node in state.map { it.target }.toSet())
                for (join in joins[node].orEmpty())
                    if (state.containsAll(join.dependencies)) {
                        val splits = if (node != end) splits[node].orEmpty() else setOf(null)
                        yieldAll(splits.map { split ->
                            DecoupledNodeExecution(node, join, split)
                        })
                    }

        } else
            yieldAll(
                splits.getValue(start)
                    .map { split -> DecoupledNodeExecution(start, null, split) })
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
     * A simplified textual representation of the model.
     *
     * Useful for debugging tests, not useful for displaying complete information to the user
     */
    override fun toString(): String {
        var result = ""
        val model = this
        for (n in model.instances.sortedBy { it.activity }) {
            val i = model.incoming.getOrDefault(n, setOf()).map { dep -> dep.source }
            val j = model.joins.getOrDefault(n, setOf()).map { join -> join.sources.map { it } }
            val o = model.outgoing.getOrDefault(n, setOf()).map { dep -> dep.target }
            val s = model.splits.getOrDefault(n, setOf()).map { split -> split.targets.map { it } }
            result += "$i/$j -> $n -> $o/$s\n"
        }
        return result
    }

    fun structurallyEquals(other: CausalNet): Boolean {
        return instances == other.instances &&
                incoming == other.incoming &&
                outgoing == other.outgoing &&
                splits == other.splits &&
                joins == other.joins
    }

}