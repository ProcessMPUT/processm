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
    val start: ActivityInstance,
    /**
     * A unique end activity instance, either real or artificial.
     *
     * If artificial, it is up to the user to populate [outgoing] and [incoming]
     */
    val end: ActivityInstance,
    metadataHandler: MetadataHandler,
    decisionModel: DecisionModel
) :
    MetadataHandler by metadataHandler,
    DecisionModel by decisionModel {
    protected val _instances = HashSet(listOf(start, end))
    /**
     * Map from source to dependency
     */
    protected val _outgoing = HashMap<ActivityInstance, HashSet<Dependency>>()
    /**
     * Map from target to dependency
     */
    protected val _incoming = HashMap<ActivityInstance, HashSet<Dependency>>()
    protected val _splits = HashMap<ActivityInstance, HashSet<Split>>()
    protected val _joins = HashMap<ActivityInstance, HashSet<Join>>()


    /**
     * Nodes AKA instances of activities
     */
    val instances: Set<ActivityInstance> = Collections.unmodifiableSet(_instances)
    /**
     * Outgoing arcs AKA what depends on a given node
     */
    val outgoing: Map<ActivityInstance, Set<Dependency>>
        get() = Collections.unmodifiableMap(_outgoing.mapValues { (_, v) -> Collections.unmodifiableSet(v) })
    /**
     * Incoming arcs AKA what given node depends on
     */
    val incoming: Map<ActivityInstance, Set<Dependency>>
        get() = Collections.unmodifiableMap(_incoming.mapValues { (_, v) -> Collections.unmodifiableSet(v) })
    /**
     * Splits AKA what other arcs must (not) be followed at the same time when going out of a node
     */
    val splits: Map<ActivityInstance, Set<Split>>
        get() = Collections.unmodifiableMap(_splits.mapValues { (_, v) -> Collections.unmodifiableSet(v) })
    /**
     * Joins AKA what other arcs must (not) be followed at the same time when going out of a node
     */
    val joins: Map<ActivityInstance, Set<Join>>
        get() = Collections.unmodifiableMap(_joins.mapValues { (_, v) -> Collections.unmodifiableSet(v) })

}