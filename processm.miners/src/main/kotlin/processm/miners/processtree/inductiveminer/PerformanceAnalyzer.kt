package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.Trace
import processm.core.models.processtree.*

class PerformanceAnalyzer(private val tree: ProcessTree) {
    var traceId = 0
    val parallelNodes = HashSet<Parallel>()

    fun cleanNode(node: Node) {
        node.currentTraceId = 0
        node.children.forEach { cleanNode(it) }
    }

    private fun nextActivities(lastExecuted: Node?): HashSet<Pair<String, Node>> {
        val node = lastExecuted ?: tree.root!!
        val possibleActivities = HashSet<Pair<String, Node>>()

        if (node.currentTraceId == traceId && node.parent != null) {
            if (node.parent is RedoLoop) {
                // Node is first child - suggest all children != first and next node
                if (node === node.parent!!.children[0]) {
                    node.parent!!.children.stream().skip(1).forEach { cleanNode(it) }
                    node.parent!!.children.stream().skip(1).forEach { possibleActivities.addAll(nextActivities(it)) }
                    possibleActivities.addAll(nextActivities(node.parent))
                    return possibleActivities
                } else {
                    cleanNode(node.parent!!)
                    return nextActivities(node.parent)
                }
            } else if (node.parent is Parallel) {
                node.parent!!.children.forEach { child ->
                    if (child is RedoLoop && child.currentTraceId == traceId) {
                        child.children.stream().skip(1).forEach { possibleActivities.addAll(nextActivities(it)) }
                    }
                }
                possibleActivities.addAll(nextActivities(node.parent))
                return possibleActivities
            } else {
                possibleActivities.addAll(nextActivities(node.parent))
                return possibleActivities
            }
        }

        when (node) {
            is Exclusive -> {
                if (node.children.count { it.currentTraceId == traceId } == 0) {
                    node.children.forEach { possibleActivities.addAll(nextActivities(it)) }
                }
            }
            is Parallel -> {
                node.children.forEach { child ->
                    if (child.currentTraceId != traceId) possibleActivities.addAll(nextActivities(child))
                    if (child is RedoLoop && child.currentTraceId == traceId) {
                        child.children.stream().skip(1).forEach { possibleActivities.addAll(nextActivities(it)) }
                    }
                }
            }
            is Sequence -> {
                for (child in node.children) {
                    if (child.currentTraceId == traceId) continue
                    else {
                        possibleActivities.addAll(nextActivities(child))
                        break
                    }
                }
            }
            is RedoLoop -> {
                if (node.children[0].currentTraceId != traceId) {
                    possibleActivities.addAll(nextActivities(node.children[0]))
                } else {
                    node.children.stream().skip(1).forEach { possibleActivities.addAll(nextActivities(it)) }
                }
            }
            is ProcessTreeActivity -> {
                if (node.currentTraceId != traceId) possibleActivities.add(node.name to node)
            }
        }

        return possibleActivities
    }

    private fun assignAsExecuted(node: Node) {
        when (node) {
            is ProcessTreeActivity -> {
                node.currentTraceId = traceId
                node.analyzedTracesIds.add(traceId)

                parallelNodes.clear()
                var n = node.parent
                while (n != null) {
                    if (n is Parallel) parallelNodes.add(n)
                    n = n.parent
                }
            }
            is Sequence, is Parallel -> {
                if (node.children.all { it.currentTraceId == traceId }) {
                    node.currentTraceId = traceId
                    node.analyzedTracesIds.add(traceId)
                }
            }
            is Exclusive -> {
                if (node.children.any { it.currentTraceId == traceId }) {
                    node.currentTraceId = traceId
                    node.analyzedTracesIds.add(traceId)
                }
            }
            is RedoLoop -> {
                if (node.children[0].currentTraceId == traceId) {
                    node.currentTraceId = traceId
                    node.analyzedTracesIds.add(traceId)
                }
            }
        }

        if (node.parent != null) assignAsExecuted(node.parent!!)
    }

    fun analyze(trace: Trace) {
        // Increment - new trace analyze
        traceId++
        var lastExecuted: Node? = null

        trace.events.forEach { event ->
            val name = event.conceptName ?: ""
            println("Execute: $name")

            while (true) {
                val activities = nextActivities(lastExecuted)
                parallelNodes.forEach { activities.addAll(nextActivities(it)) }

                val node = activities.firstOrNull { it.first == name }?.second
                val silentNode = activities.firstOrNull { it.second is SilentActivity }?.second

                if (node != null) {
                    assignAsExecuted(node)
                    lastExecuted = node
                    break
                } else if (silentNode != null) {
                    assignAsExecuted(silentNode)
                    lastExecuted = silentNode
                } else {
                    println("########## ################")
                    println("Can not execute $name")
                    println("-----------------------")
                    return
                }
            }
        }

        // execute remaining silent activities
        while (tree.root!!.currentTraceId != traceId) {
            val activities = nextActivities(lastExecuted)
            parallelNodes.forEach { activities.addAll(nextActivities(it)) }
            val silentNode = activities.firstOrNull { it.second is SilentActivity }?.second ?: break
            assignAsExecuted(silentNode)
            lastExecuted = silentNode
        }

        if (tree.root!!.currentTraceId == traceId) {
            println("-----------------------")
            tree.successAnalyzedTracesIds.add(traceId)
        }
    }

    fun fitness(): Double {
        return tree.successAnalyzedTracesIds.size / (traceId * 1.0)
    }

    fun precision(): Double {
        if (tree.root == null) return 0.0
        assignPrecision(tree.root!!)
        return tree.root!!.precision
    }

    private fun assignPrecision(node: Node) {
        if (node is ProcessTreeActivity) {
            node.precision = if (node.analyzedTracesIds.isEmpty()) 0.0 else 1.0
        } else {
            node.children.forEach { assignPrecision(it) }
            node.precision = node.children.sumByDouble { it.precision } / (1.0 * node.children.size)
        }
    }
}
