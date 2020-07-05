package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.Trace
import processm.core.models.processtree.*
import java.util.*

class PerformanceAnalyzer(private val tree: ProcessTree) {
    var traceId = 0
    var alreadyTested: Node? = null

    private fun cleanChildren(n: Node?) {
        println("\t$n")
        if (n != null) {
            n.currentTraceId = 0
            n.children.forEach { cleanChildren(it) }
        }
    }

    fun analyze(trace: Trace) {
        // Increment - new trace analyze
        traceId++
        val loops = Stack<Node>()
        var cleanUp = false


        // Analyze whole events
        trace.events.forEach { event ->
            alreadyTested = null

            println(event.conceptName)
            // Start in root always
            var again = true

            while (again) {
                var assignment = false
                var currentSubTree: Node? = tree.root
                again = false
                if (loops.isNotEmpty()) {
                    val a = loops.first()
                    if (a.activitiesSet.contains(event.conceptName)) {
                        currentSubTree = a
                    } else loops.remove(a)
                }

                if (cleanUp) {
                    println("CLEAN UP $loops")
                    cleanUp = false
                    loops.firstOrNull()?.children.orEmpty().forEach {
                        cleanChildren(it)
                    }
                }

                while (currentSubTree != null) {
//                    if (cleanUp) {
//                        println("CLEAN UP $loops")
//                        cleanUp = false
//                        cleanChildren(loops.firstOrNull()?.children?.firstOrNull())
//                    }

                    var next = nextCheck(currentSubTree, event.conceptName ?: "", traceId)
                    println("currentSubTree: $currentSubTree \t\t next: $next")

                    if (next != null) {
                        if (next is SilentActivity) {
                            again = true
                        }
                        if (currentSubTree is RedoLoop) {
//                            println("INSIDE")
                            loops.push(currentSubTree)
                            if (next != currentSubTree.children.firstOrNull()) cleanUp = true
//                            println(cleanUp)
                        }
                        if (isCompletedNode(next, event.conceptName ?: "", traceId)) {
                            println("1. Node $next completed")
                            assignment = true
                            next.currentTraceId = traceId
                            next.analyzedTracesIds.add(traceId)

                            next = next.parent
                            while (next != null) {
                                if (isCompletedNode(next, event.conceptName ?: "", traceId)) {
                                    println("2. Node $next completed")
                                    next.currentTraceId = traceId
                                    next.analyzedTracesIds.add(traceId)
                                    next = next.parent
                                } else break
                            }

                            // After assignment - break loop
                            break
                        }

                        // Assign next subTree
                        currentSubTree = next
                    } else {
                        // Rollback to parent
                        currentSubTree = currentSubTree.parent
                    }
                }

                if (!assignment) return
            }
        }

        if (isCompletedNode(tree.root!!, "", traceId)) {
            tree.successAnalyzedTracesIds.add(traceId)
            println("Trace $traceId fit to model")
        }
    }

    private fun nextCheck(node: Node, activityName: String, currentTraceId: Int): Node? {
        when (node) {
            is Exclusive, is Parallel -> {
                alreadyTested = node.children.firstOrNull {
                    it != alreadyTested &&
                            it.currentTraceId != currentTraceId &&
                            it.activitiesSet.contains(activityName)
                } ?: node.children.firstOrNull {
                    it != alreadyTested && it.currentTraceId != currentTraceId && it.activitiesSet.contains(
                        ""
                    )
                }
                return alreadyTested
            }
            is Sequence -> {
                for (n in node.children) {
                    // Used
                    if (n.currentTraceId == currentTraceId) continue
                    // Silent activity not used
                    if (n.currentTraceId != currentTraceId && n.activitiesSet.contains("")) return n
                    // Expected activity not used
                    if (n.currentTraceId != currentTraceId && n.activitiesSet.contains(activityName)) return n
                    // Not expected activity - error
                    return null
                }
            }
            is RedoLoop -> {
                for (n in node.children) {
                    if (n.currentTraceId == currentTraceId) continue
                    // Silent activity
                    if (n.currentTraceId != currentTraceId && n.activitiesSet.contains("")) return n
                    // Expected activity
                    if (n.currentTraceId != currentTraceId && n.activitiesSet.contains(activityName)) return n
                    // Not expected activity - error
                    return null
                }
            }
        }

        return null
    }

    private fun isCompletedNode(node: Node, activityName: String, currentTraceId: Int): Boolean {
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
