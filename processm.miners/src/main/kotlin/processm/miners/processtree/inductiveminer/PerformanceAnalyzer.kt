package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.Trace
import processm.core.models.processtree.*

class PerformanceAnalyzer(private val tree: ProcessTree) {
    var traceId = 0
    private val alreadyTested = LinkedHashSet<Node?>()
    private val alreadyTestedHistory = LinkedHashSet<Node?>()

    fun analyze(trace: Trace, increment: Boolean = true) {
        // Start with root
        var currentSubTree: Node? = tree.root

        // Increment - new trace analyze
        if (increment) traceId++

        // Analyze whole events
        trace.events.forEach { event ->
            if (increment) alreadyTested.clear()

            val name = event.conceptName ?: ""
            println("Szukam $name")
            var next: Node?

            while (!currentSubTree!!.activitiesSet.contains(name) && currentSubTree!!.parent != null) {
                currentSubTree = currentSubTree!!.parent ?: tree.root
            }

            do {
                next = nextCheck(currentSubTree!!, name)
                if (next != null) {
                    println("- zmiana z ${currentSubTree} \t\t\t ${next}")
                    currentSubTree = next
                }

                if (isCompletedNode(currentSubTree!!, name)) {
                    do {
                        println("Spełniono $currentSubTree")
                        currentSubTree!!.currentTraceId = traceId
                        currentSubTree!!.analyzedTracesIds.add(traceId)
                        currentSubTree = currentSubTree!!.parent ?: tree.root
                        println("Wycofanie do $currentSubTree")
                    } while (isCompletedNode(
                            currentSubTree!!,
                            name
                        ) && currentSubTree !is RedoLoop && currentSubTree!!.parent != null
                    )
                }
            } while (next != null)
        }

        if (isCompletedNode(tree.root!!, "")) {
            tree.successAnalyzedTracesIds.add(traceId)
            println("Trace $traceId fit to model")
        } else {
            var i = 1
            do {
                alreadyTested.clear()
                alreadyTested.addAll(alreadyTestedHistory.take(i))
                clean(tree.root!!)
                analyze(trace, increment = false)
                i++
            } while (i < alreadyTestedHistory.size)
        }
        println("========================================")
    }

    private fun clean(n: Node) {
        n.currentTraceId = 0
        n.children.forEach { clean(it) }
    }

    private fun nextCheck(node: Node, activityName: String, currentTraceId: Int = traceId): Node? {
        when (node) {
            is Exclusive -> {
                if (node.children.count { it.currentTraceId == traceId } == 0) {
                    val nm = node.children.firstOrNull {
                        it.currentTraceId != currentTraceId && it.activitiesSet.contains(activityName)
                    }
                    if (nm != null) return nm

                    val n = node.children.firstOrNull {
                        it !in alreadyTested && it.currentTraceId != currentTraceId && it.activitiesSet.contains("")
                    }
                    alreadyTested.add(n)
                    alreadyTestedHistory.add(n)
                    return n
                } else {
                    // Exclusive only one child
                    return null
                }
            }
            is Parallel -> {
                return node.children.firstOrNull {
                    it.currentTraceId != currentTraceId && it.activitiesSet.contains(
                        activityName
                    )
                } ?: node.children.firstOrNull {
                    it !in alreadyTested && it.currentTraceId != currentTraceId && it.activitiesSet.contains(
                        ""
                    )
                }
            }
            is Sequence -> {
                for (n in node.children) {
                    // Used
                    if (n.currentTraceId == currentTraceId) continue
                    // Silent activity not used
                    if (n.currentTraceId != currentTraceId && n.activitiesSet.contains("")) return n
                    // Expected activity not used
                    if (n.currentTraceId != currentTraceId && n.activitiesSet.contains(activityName)) return n
                }
            }
            is RedoLoop -> {
                for (n in node.children) {
                    if (n.currentTraceId == currentTraceId) continue
                    // Silent activity
                    if (n.currentTraceId != currentTraceId && n.activitiesSet.contains("")) return n
                    // Expected activity
                    if (n.currentTraceId != currentTraceId && n.activitiesSet.contains(activityName)) return n
                }
            }
        }

        return null
    }

    private fun isCompletedNode(node: Node, activityName: String, currentTraceId: Int = traceId): Boolean {
        when (node) {
            is SilentActivity -> return true
            is ProcessTreeActivity -> {
                return node.name == activityName
            }
            is Sequence, is Parallel -> {
                return node.children.all { it.currentTraceId == currentTraceId }
            }
            is Exclusive -> {
                return node.children.any { it.currentTraceId == currentTraceId }
            }
            is RedoLoop -> {
                // Only first element
                if (node.children.first().currentTraceId == currentTraceId &&
                    node.children.stream().skip(1).noneMatch { it.currentTraceId == currentTraceId }
                ) return true

                // First and extra element
                if (node.children.first().currentTraceId == currentTraceId &&
                    node.children.stream().skip(1).anyMatch { it.currentTraceId == currentTraceId }
                ) return true
            }
        }

        return false
    }
}
