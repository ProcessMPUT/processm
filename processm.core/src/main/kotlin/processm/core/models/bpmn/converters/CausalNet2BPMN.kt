package processm.core.models.bpmn.converters

import processm.core.models.bpmn.BPMNModel
import processm.core.models.bpmn.jaxb.*
import processm.core.models.causalnet.*
import processm.core.models.causalnet.Node
import javax.xml.bind.JAXBElement
import javax.xml.namespace.QName

/**
 * Performs conversion from causal net to BPMN Process
 * @see [CausalNet].[toBPMN]
 */
internal class CausalNet2BPMN(private val cnet: CausalNet, private val nameGateways: Boolean, private val targetNamespace: String) {

    private fun <T : TFlowNode> wrap(a: Node, result: T): T {
        result.name = a.name
        return result
    }

    private val nodes = HashMap<Node, TFlowNode>()
    private val factory = ObjectFactory()
    private var ctr = 0
    private val flowElements = ArrayList<JAXBElement<out TFlowElement>>()
    private lateinit var deps: Map<Dependency, Pair<TFlowNode, TFlowNode>>
    private val splitsExclusive = HashMap<Node, TFlowNode>()
    private val joinsExclusive = HashMap<Node, TFlowNode>()
    private val splitsParallel = HashMap<Split, TFlowNode>()
    private val joinsParallel = HashMap<Join, TFlowNode>()

    private fun add(n: TFlowElement) {
        if (n.id == null) {
            n.id = "id_$ctr"
            ctr += 1
        }
        val e = when (n) {
            is TStartEvent -> factory.createStartEvent(n)
            is TEndEvent -> factory.createEndEvent(n)
            is TTask -> factory.createTask(n)
            is TExclusiveGateway -> factory.createExclusiveGateway(n)
            is TParallelGateway -> factory.createParallelGateway(n)
            is TSequenceFlow -> factory.createSequenceFlow(n)
            else -> throw UnsupportedOperationException("Unsupported flow element ${n::class}")
        }
        flowElements.add(e)
    }

    private fun link(src: TFlowNode, dst: TFlowNode) {
        require(src !== dst)
        val link = TSequenceFlow()
        link.sourceRef = src
        link.targetRef = dst
        add(link)
        src.outgoing.add(QName(link.id))
        dst.incoming.add(QName(link.id))
    }

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
            val relevantSplits = cnet.splits.getValue(dep.source).filter { dep in it }
            val relevantJoins = cnet.joins.getValue(dep.target).filter { dep in it }
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
                .associateWithTo(nodes) { wrap(it, TTask()) }
        nodes[cnet.start] = wrap(cnet.start, TStartEvent())
        nodes[cnet.end] = wrap(cnet.end, TEndEvent())
        nodes.putAll(tasks)
        for (node in nodes.values)
            add(node)
        createSplitGateways()
        createJoinGateways()
        createDependencyGateways()
        linkSplits()
        linkJoins()
        val process = TProcess()
        process.flowElement.addAll(flowElements)
        val model = TDefinitions()
        model.targetNamespace = targetNamespace
        model.rootElement.add(factory.createProcess(process))
        return BPMNModel(model)
    }
}

/**
 * Converts [this] to a [BPMNModel] with a single process.
 *
 * @param nameGateways True if gateways in the resulting BPMN are to be named
 * @param targetNamespace BPMN spec: This attribute identifies the namespace associated with the Definition and follows the convention established by XML Schema.
 */
fun CausalNet.toBPMN(nameGateways: Boolean = false, targetNamespace: String = "") = CausalNet2BPMN(this, nameGateways, targetNamespace).toBPMN()