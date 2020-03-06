package processm.core.models.causalnet

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
    metadataHandler: MetadataHandler,
    decisionModel: DecisionModel
) :
    MetadataHandler by metadataHandler,
    DecisionModel by decisionModel {
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
     * Returns true if the given join is present in the model and false otherwise
     */
    fun contains(join: Join): Boolean {
        return _joins[join.target]?.contains(join) == true
    }

    /**
     * Returns true if the given split is present in the model and false otherwise
     */
    fun contains(split: Split): Boolean {
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
            val i = model.incoming.getOrDefault(n, setOf()).map { dep -> dep.source.activity }
            val j = model.joins.getOrDefault(n, setOf()).map { join -> join.sources.map { it.activity } }
            val o = model.outgoing.getOrDefault(n, setOf()).map { dep -> dep.target.activity }
            val s = model.splits.getOrDefault(n, setOf()).map { split -> split.targets.map { it.activity } }
            result += "$i/$j -> ${n.activity} -> $o/$s\n"
        }
        return result
    }

}