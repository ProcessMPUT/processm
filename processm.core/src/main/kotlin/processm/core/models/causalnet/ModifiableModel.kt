package processm.core.models.causalnet

import processm.core.models.metadata.DefaultModifiableMetadataHandler
import processm.core.models.metadata.ModifiableMetadataHandler

/**
 * The default implementation of a causal net model
 */
class ModifiableModel(
    start: ActivityInstance = ActivityInstance(Activity("start", true)),
    end: ActivityInstance = ActivityInstance(Activity("end", true)),
    private val metadataHandler: ModifiableMetadataHandler = DefaultModifiableMetadataHandler(),
    var decisionModel: DecisionModel = AlwaysFirstDecisionModel()
) : Model(start, end, metadataHandler, decisionModel), ModifiableMetadataHandler by metadataHandler {

    fun addInstance(vararg a: ActivityInstance) {
        _instances.addAll(a)
    }

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

    fun addDependency(source: ActivityInstance, target: ActivityInstance): Dependency {
        return addDependency(Dependency(source, target))
    }

    fun addSplit(split: Split) {
        if (!_outgoing.getValue(split.source).containsAll(split.dependencies))
            throw IllegalArgumentException()
        _splits.getOrPut(split.source, { HashSet() }).add(split)
    }

    fun addJoin(join: Join) {
        if (!_incoming.getValue(join.target).containsAll(join.dependencies))
            throw IllegalArgumentException()
        _joins.getOrPut(join.target, { HashSet() }).add(join)
    }

}