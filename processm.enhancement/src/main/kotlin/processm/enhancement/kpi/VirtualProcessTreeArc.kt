package processm.enhancement.kpi

import processm.core.models.commons.Arc
import processm.core.models.processtree.*

//TODO possibly should be equipped with an actual path
data class VirtualProcessTreeArc(override val source: ProcessTreeActivity, override val target: ProcessTreeActivity) :
    Arc


fun ProcessTree.generateArcs(includeSilent: Boolean = false): Set<VirtualProcessTreeArc> {
    val result = HashSet<VirtualProcessTreeArc>()
    fun process(node: Node, causes: List<ProcessTreeActivity>): List<ProcessTreeActivity> {
        when (node) {
            is ProcessTreeActivity -> {
                if (!node.isSilent || includeSilent) {
                    causes.mapTo(result) { VirtualProcessTreeArc(it, node) }
                    return listOf(node)
                } else
                    return emptyList()
            }

            is Sequence -> {
                var newCauses = causes
                for (child in node.children) {
                    newCauses = process(child, newCauses)
                }
                return newCauses
            }

            is Exclusive, is Parallel -> {
                return node.children.flatMap { process(it, causes) }
            }

            is RedoLoop -> {
                val doPhase1 = process(node.children[0], causes)
                val redoPhase = node.children.subList(1, node.children.size).flatMap { process(it, doPhase1) }
                val doPhase2 = process(node.children[0], redoPhase)
                assert(doPhase1 == doPhase2)
                return doPhase1
            }

            else -> throw NotImplementedError("${node::class} is not supported")
        }
    }
    process(requireNotNull(root), emptyList())
    return result
}