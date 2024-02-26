package processm.enhancement.kpi

import kotlinx.serialization.Serializable
import processm.core.models.commons.CausalArc
import processm.core.models.processtree.*
import processm.helpers.cartesianProduct

/**
 * An [CausalArc] using [ProcessTreeActivity]. This is not a real structure in a process tree, but rather an abstraction
 * representing causal dependency.
 */
@Serializable
data class VirtualProcessTreeCausalArc(override val source: ProcessTreeActivity, override val target: ProcessTreeActivity) :
    CausalArc

/**
 * A data structure for a process tree similar to a join from Causal Nets.
 *
 * @param sources The set of activities that must be executed jointly before [target] can be executed
 */
data class VirtualProcessTreeMultiArc(val sources: Set<ProcessTreeActivity>, val target: ProcessTreeActivity) {
    fun toArcs() = sources.asSequence().map { VirtualProcessTreeCausalArc(it, target) }

    override fun toString(): String =
        sources.joinToString(prefix = "[", separator = ", ", postfix = "]") { it.name } + " -> ${target.name}"
}

/**
 * Computes [VirtualProcessTreeMultiArc] by applying the semantics of the nodes and computing sets of possible predecessors/successors.
 */
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
                node.children.map { child -> process(causes, child).flatMapTo(ArrayList()) { it } }.cartesianProduct()
                    .toList()
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
