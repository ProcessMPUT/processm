package processm.enhancement.kpi

import processm.core.helpers.cartesianProduct
import processm.core.models.commons.Arc
import processm.core.models.processtree.*

data class VirtualProcessTreeArc(override val source: ProcessTreeActivity, override val target: ProcessTreeActivity) :
    Arc

data class VirtualProcessTreeMultiArc(val sources: Set<ProcessTreeActivity>, val target: ProcessTreeActivity) {
    fun toArcs() = sources.asSequence().map { VirtualProcessTreeArc(it, target) }
}


fun ProcessTree.generateArcs(includeSilent: Boolean = false): Set<VirtualProcessTreeMultiArc> {
    val result = HashSet<VirtualProcessTreeMultiArc>()
    fun process(causes: List<List<ProcessTreeActivity>>, node: Node): List<List<ProcessTreeActivity>> =
        when (node) {
            is ProcessTreeActivity -> {
                if (!node.isSilent || includeSilent) {
                    causes.mapTo(result) { VirtualProcessTreeMultiArc(it.toSet(), node) }
                    listOf(listOf(node))
                } else
                    emptyList()
            }

            is Sequence -> {
                node.children.fold(causes, ::process)
            }

            is Parallel -> {
                node.children.map { process(causes, it).flatten() }.cartesianProduct().toList()
            }

            is Exclusive -> {
                node.children.flatMap { process(causes, it) }
            }

            is RedoLoop -> {
                val doPhase1 = process(causes, node.children[0])
                val redoPhase = node.children.subList(1, node.children.size).flatMap { process(doPhase1, it) }
                // even though we ignore the returned value, this second call to process is necessary to update the content of result
                val doPhase2 = process(redoPhase, node.children[0])
                assert(doPhase1 == doPhase2)
                doPhase1
            }

            else -> throw NotImplementedError("${node::class} is not supported")
        }

    process(emptyList(), requireNotNull(root))
    return result
}