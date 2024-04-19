package processm.core.models.bpmn.converters

import processm.core.models.bpmn.BPMNModel
import processm.core.models.bpmn.jaxb.*
import processm.core.models.causalnet.*
import processm.core.models.causalnet.Node

/**
 * Performs conversion from causal net to BPMN Process
 * @see [CausalNet].[toBPMN]
 */
internal class CausalNet2BPMN(
    private val cnet: CausalNet,
    private val nameGateways: Boolean,
    private val targetNamespace: String
) : ToBPMN() {


    private val nodes = HashMap<Node, TFlowNode>()
    private lateinit var deps: Map<Dependency, Pair<TFlowNode, TFlowNode>>
    private val splitsExclusive = HashMap<Node, TFlowNode>()
    private val joinsExclusive = HashMap<Node, TFlowNode>()
    private val splitsParallel = HashMap<Split, TFlowNode>()
    private val joinsParallel = HashMap<Join, TFlowNode>()

    private fun linkSplits() {
        for ((src, splits) in cnet.splits) {
            val exclusive = splitsExclusive.getValue(src)
            for (split in splits) {
                val parallel = splitsParallel.getValue(split)
                for (dep in split.dependencies) {
                    val left = deps.getValue(dep).first
                    if (left != parallel)
                        link(parallel, left)
                }
                if (parallel != exclusive)
                    link(exclusive, parallel)
            }
        }
    }

    private fun linkJoins() {
        for ((dst, joins) in cnet.joins) {
            val exclusive = joinsExclusive.getValue(dst)
            for (join in joins) {
                val parallel = joinsParallel.getValue(join)
                for (dep in join.dependencies) {
                    val right = deps.getValue(dep).second
                    if (right != parallel)
                        link(right, parallel)
                }
                if (parallel != exclusive)
                    link(parallel, exclusive)
            }
        }
    }

    private fun exclusiveGateway(name: String): TExclusiveGateway {
        val gw = TExclusiveGateway()
        if (nameGateways)
            gw.name = name
        add(gw)
        return gw
    }

    private fun parallelGateway(name: String): TParallelGateway {
        val gw = TParallelGateway()
        if (nameGateways)
            gw.name = name
        add(gw)
        return gw
    }

    private fun createSplitGateways() {
        splitsExclusive.clear()
        splitsParallel.clear()
        for ((src, splits) in cnet.splits) {
            val base = nodes.getValue(src)
            val exclusive = if (splits.size > 1) {
                val gw = exclusiveGateway("splits:$src")
                link(base, gw)
                gw
            } else
                base
            splitsExclusive[src] = exclusive
            for (split in splits) {
                splitsParallel[split] = if (split.size > 1) {
                    val gw = parallelGateway("$split")
                    link(exclusive, gw)
                    gw
                } else
                    exclusive
            }
        }
    }

    private fun createJoinGateways() {
        joinsExclusive.clear()
        joinsParallel.clear()
        for ((dst, joins) in cnet.joins) {
            val base = nodes.getValue(dst)
            val exclusive = if (joins.size > 1) {
                val gw = exclusiveGateway("joins:$dst")
                link(gw, base)
                gw
            } else
                base
            joinsExclusive[dst] = exclusive
            for (join in joins) {
                joinsParallel[join] = if (join.size >= 2) {
                    val gw = parallelGateway("$join")
                    link(gw, exclusive)
                    gw
                } else
                    exclusive
            }
        }
    }

    private fun createDependencyGateways() {
        deps = cnet.dependencies.associateWith { dep ->
            val relevantSplits = cnet.splits[dep.source]?.filter { dep in it }.orEmpty()
            val relevantJoins = cnet.joins[dep.target]?.filter { dep in it }.orEmpty()
            if (relevantSplits.size == 1 && relevantJoins.size == 1) {
                val left = splitsParallel.getValue(relevantSplits.single())
                val right = joinsParallel.getValue(relevantJoins.single())
                link(left, right)
                return@associateWith left to right
            } else {
                val tmp = exclusiveGateway("$dep")
                return@associateWith tmp to tmp
            }
        }
    }


    /**
     * Performs the actual conversion
     */
    fun toBPMN(): BPMNModel {
        nodes.clear()
        val tasks = cnet.activities
            .filter { it != cnet.start && it != cnet.end }
            .associateWithTo(nodes) { add(TTask().apply { name = it.name }) }
        nodes[cnet.start] = add(TStartEvent().apply { name = cnet.start.name })
        nodes[cnet.end] = add(TEndEvent().apply { name = cnet.end.name })
        nodes.putAll(tasks)
        createSplitGateways()
        createJoinGateways()
        createDependencyGateways()
        linkSplits()
        linkJoins()
        return finish()
    }
}

/**
 * Converts [this] to a [BPMNModel] with a single process.
 *
 * @param nameGateways True if gateways in the resulting BPMN are to be named
 * @param targetNamespace BPMN spec: This attribute identifies the namespace associated with the Definition and follows the convention established by XML Schema.
 */
fun CausalNet.toBPMN(nameGateways: Boolean = false, targetNamespace: String = "") =
    CausalNet2BPMN(this, nameGateways, targetNamespace).toBPMN()
