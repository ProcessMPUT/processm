package processm.core.models.causalnet

import processm.core.models.commons.AbstractActivity
import processm.core.models.commons.AbstractModel
import processm.core.models.metadata.MetadataHandler
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * A read-only causal net model
 */
abstract class Model(
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
    AbstractModel,
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

    override val activities: Sequence<Node>
        get() = instances.asSequence()

    override val startActivities: Sequence<AbstractActivity> = sequenceOf(start)

    override val endActivities: Sequence<AbstractActivity> = sequenceOf(end)

    override val decisionPoints: Sequence<DecisionPoint>
        get() = splits.entries.asSequence().map { DecisionPoint(it.key, it.value) } +
                joins.entries.asSequence().map { DecisionPoint(it.key, it.value) }

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
                    .map { split -> DecoupledNodeExecution(start,  null, split) })
    }
}