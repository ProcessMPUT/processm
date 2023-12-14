package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID

/**
 * A mapping from a DB object via attribute names to referenced/referencing objects, grouped by their classes
 */
typealias ObjectGraph = Map<RemoteObjectID, Map<String, Map<EntityID<Int>, Set<RemoteObjectID>>>>

/**
 * Returns a copy of the object graph with arcs reversed
 */
internal fun ObjectGraph.reverse(): ObjectGraph {
    val result = HashMap<RemoteObjectID, HashMap<String, HashMap<EntityID<Int>, HashSet<RemoteObjectID>>>>()
    for ((source, attributes) in this) {
        for ((attribute, targets) in attributes)
            for (target in targets.values.flatten())
                result
                    .computeIfAbsent(target) { HashMap() }
                    .computeIfAbsent(attribute) { HashMap() }
                    .computeIfAbsent(source.classId) { HashSet() }
                    .add(source)
    }
    return result
}

/**
 * The set of objects reachable from objects of [start] according to the structure in [descriptor] while traveling up the graph
 */
internal fun ObjectGraph.upwards(
    start: Collection<RemoteObjectID>,
    descriptor: AutomaticEtlProcessDescriptor
): Set<RemoteObjectID> {
    val localUpward = HashSet<RemoteObjectID>()
    val queue = ArrayDeque<RemoteObjectID>()
    queue.addAll(start)
    while (queue.isNotEmpty()) {
        val item = queue.removeFirst()
        if (item.classId in descriptor.identifyingClasses)
            localUpward.add(item)
        for (r in descriptor.graph.outgoingEdgesOf(item.classId)) {
            val candidates = this[item]?.get(r.attributeName)?.get(r.targetClass) ?: continue
            queue.addAll(candidates)
        }
    }
    return localUpward
}

/**
 * The set of objects reachable from objects of [localUpward] according to the structure in [descriptor] while traveling down the graph
 */
internal fun ObjectGraph.downwards(
    localUpward: Collection<RemoteObjectID>,
    descriptor: AutomaticEtlProcessDescriptor
): Sequence<RemoteObjectID> = sequence {
    val reject = localUpward.mapTo(HashSet()) { it.classId }
    reject.addAll(descriptor.convergingClasses)
    val queue = ArrayDeque<RemoteObjectID>()
    queue.addAll(localUpward)
    while (queue.isNotEmpty()) {
        val item = queue.removeFirst()
        for (r in descriptor.graph.incomingEdgesOf(item.classId)) {
            if (r.sourceClass in reject)
                continue
            val sources = this@downwards[item]?.get(r.attributeName)?.get(r.sourceClass) ?: continue
            queue.addAll(sources)
            yieldAll(sources)
        }
    }
}