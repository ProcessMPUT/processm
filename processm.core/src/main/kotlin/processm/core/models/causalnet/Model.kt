package processm.core.models.causalnet

import processm.core.models.metadata.MetadataHandler

/**
 * A read-only causal net model
 */
abstract class Model(
    val start: ActivityInstance,
    val end: ActivityInstance,
    metadataHandler: MetadataHandler,
    decisionModel: DecisionModel
) :
    MetadataHandler by metadataHandler,
    DecisionModel by decisionModel {
    protected val _instances = HashSet(listOf(start, end))
    protected val _outgoing = HashMap<ActivityInstance, HashSet<Dependency>>()  //from source to dependency
    protected val _incoming = HashMap<ActivityInstance, HashSet<Dependency>>()  //from target to dependency
    protected val _splits = HashMap<ActivityInstance, HashSet<Split>>()
    protected val _joins = HashMap<ActivityInstance, HashSet<Join>>()


    /**
     * Nodes AKA instances of activities
     */
    val instances: Set<ActivityInstance> = _instances
    /**
     * Outgoing arcs AKA what depends on a given node
     */
    val outgoing: Map<ActivityInstance, Set<Dependency>> = _outgoing
    /**
     * Incoming arcs AKA what given node depends on
     */
    val incoming: Map<ActivityInstance, Set<Dependency>> = _incoming
    /**
     * Splits AKA what other arcs must (not) be followed at the same time when going out of a node
     */
    val splits: Map<ActivityInstance, Set<Split>> = _splits
    /**
     * Joins AKA what other arcs must (not) be followed at the same time when going out of a node
     */
    val joins: Map<ActivityInstance, Set<Join>> = _joins


}