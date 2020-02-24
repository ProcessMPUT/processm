package processm.core.models.causalnet

import processm.core.models.metadata.DefaultMutableMetadataHandler
import processm.core.models.metadata.MutableMetadataHandler

/**
 * The default implementation of a causal net model
 */
class MutableModel(
    start: ActivityInstance = ActivityInstance(Activity("start", true)),
    end: ActivityInstance = ActivityInstance(Activity("end", true)),
    private val metadataHandler: MutableMetadataHandler = DefaultMutableMetadataHandler(),
    var decisionModel: DecisionModel = AlwaysFirstDecisionModel()
) : Model(start, end, metadataHandler, decisionModel), MutableMetadataHandler by metadataHandler {

    /**
     * Adds a (set of) new activity instance(s) to the model
     */
    fun addInstance(vararg a: ActivityInstance) {
        _instances.addAll(a)
    }

    /**
     * Adds a dependency between activity instances already present in the model
     */
    fun addDependency(d: Dependency): Dependency {
        if (d.source !in _instances) {
            throw IllegalArgumentException("Unknown activity instance ${d.source}")
        }
        if (d.target !in _instances) {
            throw IllegalArgumentException("Unknown activity instance ${d.target}")
        }
        _outgoing.getOrPut(d.source, { HashSet() }).add(d)
        _incoming.getOrPut(d.target, { HashSet() }).add(d)
        return d
    }

    /**
     * Adds a dependency between activity instances already present in the model
     */
    fun addDependency(source: ActivityInstance, target: ActivityInstance): Dependency {
        return addDependency(Dependency(source, target))
    }

    /**
     * Adds a split between dependencies already present in the model
     */
    fun addSplit(split: Split) {
        if (!_outgoing.getValue(split.source).containsAll(split.dependencies))
            throw IllegalArgumentException()
        _splits.getOrPut(split.source, { HashSet() }).add(split)
    }

    /**
     * Adds a join between dependencies already present in the model
     */
    fun addJoin(join: Join) {
        if (!_incoming.getValue(join.target).containsAll(join.dependencies))
            throw IllegalArgumentException()
        _joins.getOrPut(join.target, { HashSet() }).add(join)
    }

}