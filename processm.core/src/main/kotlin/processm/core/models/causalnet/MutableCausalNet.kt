package processm.core.models.causalnet

import processm.core.models.metadata.DefaultMutableMetadataHandler
import processm.core.models.metadata.MutableMetadataHandler
import processm.helpers.mapToSet

/**
 * The default implementation of a causal net model
 */
class MutableCausalNet(
    start: Node = Node("start", isSilent = true),
    end: Node = Node("end", isSilent = true),
    private val metadataHandler: MutableMetadataHandler = DefaultMutableMetadataHandler()
) : CausalNet(start, end, metadataHandler),
    MutableMetadataHandler by metadataHandler {

    /**
     * Replaces the [start] activity with the given one.
     * @return The previous start node.
     */
    fun setStart(start: Node): Node {
        val prev = this.start
        this.start = start
        return prev
    }

    /**
     * Replaces the [end] activity with the given one.
     * @return The previous end node.
     */
    fun setEnd(end: Node): Node {
        val prev = this.end
        this.end = end
        return prev
    }

    /**
     * Adds a (set of) new activity instance(s) to the model
     */
    fun addInstance(vararg a: Node) {
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
        _outgoing.computeIfAbsent(d.source, { HashSet() }).add(d)
        _incoming.computeIfAbsent(d.target, { HashSet() }).add(d)
        return d
    }

    /**
     * Adds a dependency between activity instances already present in the model
     */
    fun addDependency(source: Node, target: Node): Dependency {
        return addDependency(Dependency(source, target))
    }

    /**
     * Adds a split between dependencies already present in the model
     */
    fun addSplit(split: Split) {
        require(
            _outgoing.getValue(split.source).containsAll(split.dependencies)
        ) { "Not all dependencies are in the causal net" }
        _splits.computeIfAbsent(split.source, { ArrayList() }).apply {
            require(all { it.dependencies != split.dependencies }) { "Split already present in the causal net" }
            add(split)
        }
    }

    /**
     * Adds a join between dependencies already present in the model
     */
    fun addJoin(join: Join) {
        require(
            _incoming.getValue(join.target).containsAll(join.dependencies)
        ) { "Not all dependencies are in the causal net" }
        _joins.computeIfAbsent(join.target, { ArrayList() }).apply {
            require(all { it.dependencies != join.dependencies }) { "Join already present in the causal net" }
            add(join)
        }
    }

    /**
     * Creates an instance of this model with the same [metadataHandler]
     */
    override fun createInstance() = MutableCausalNetInstance(this, metadataHandler)

    /**
     * Removes the given [node] from this Causal net and drops all related bindings and dependencies.
     */
    fun removeInstance(node: Node) {
        assert(node in _instances)
        _splits.remove(node)?.forEach { s -> s.targets.forEach { _joins[it]?.removeIf { j -> node in j.sources } } }
        _joins.remove(node)?.forEach { j -> j.sources.forEach { _splits[it]?.removeIf { s -> node in s.targets } } }
        _outgoing.remove(node)?.forEach { dep -> _incoming[dep.target]?.removeIf { it.source == node } }
        _incoming.remove(node)?.forEach { dep -> _outgoing[dep.source]?.removeIf { it.target == node } }
        _instances.remove(node)
    }

    /**
     * Removes the given split.
     *
     * Silently ignores if the split is not present in the model.
     */
    fun removeSplit(split: Split) {
        _splits[split.source]?.remove(split)
    }

    /**
     * Removes the given join.
     *
     * Silently ignores if the join is not present in the model.
     */
    fun removeJoin(join: Join) {
        _joins[join.target]?.remove(join)
    }

    /**
     * Remove all bindings from the model
     */
    fun clearBindings() {
        clearSplits()
        clearJoins()
    }

    /**
     * Remove all splits from the model
     */
    fun clearSplits() {
        _splits.clear()
    }

    /**
     * Remove all joins from the model
     */
    fun clearJoins() {
        _joins.clear()
    }

    /**
     * Removes all bindings for a given node
     */
    fun clearBindingsFor(node: Node) {
        _joins.remove(node)
        _splits.remove(node)
    }

    fun clearDependencies() {
        _incoming.clear()
        _outgoing.clear()
        clearSplits()
        clearJoins()
    }

    /**
     * Removes all splits for [node]
     */
    fun clearSplitsFor(node: Node) {
        _splits.remove(node)
    }

    /**
     * Removes all joins for [node]
     */
    fun clearJoinsFor(node: Node) {
        _joins.remove(node)
    }

    /**
     * Adds all nodes, dependencies and bindings from [origin] to this, using [translate] to map from nodes of [origin] to nodes of this
     */
    fun copyFrom(origin: CausalNet, translate: (Node) -> Node) {
        val n2n = origin.instances.associateWith(translate)
        addInstance(*n2n.values.toTypedArray())
        val d2d = origin.outgoing.values.flatten().associateWith { dep -> Dependency(n2n.getValue(dep.source), n2n.getValue(dep.target)) }
        for (dep in d2d.values)
            addDependency(dep)
        for (split in origin.splits.values.flatten()) {
            val s = Split(split.dependencies.mapToSet { d2d.getValue(it) })
            if (s !in this)
                addSplit(s)
        }
        for (join in origin.joins.values.flatten()) {
            val j = Join(join.dependencies.mapToSet { d2d.getValue(it) })
            if (j !in this)
                addJoin(j)
        }
    }
}
