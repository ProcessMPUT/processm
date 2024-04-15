package processm.core.models.bpmn.converters

import jakarta.xml.bind.JAXBElement
import processm.core.models.bpmn.jaxb.TFlowElement
import processm.core.models.bpmn.jaxb.TFlowNode
import processm.core.models.bpmn.jaxb.TSequenceFlow

/**
 * BPMN as an abstraction is closely tied to its visual representation and the component we use to display BPMNs seems
 * to be incapable of working without some layout. This class is intended for [CausalNet2BPMN] and similar converters
 * to generate some layout of the BPMN graph. That being said, it is written in a general manner and could
 * be employed in other places.
 */
internal class Layouter<TNode, TArc>(val nodes: Set<TNode>, val arcs: Map<TArc, Pair<TNode, TNode>>) {

    companion object {
        fun fromFlowElements(flowElements: Collection<JAXBElement<out TFlowElement>>): Layouter<TFlowNode, String> {
            val nodes = HashSet<TFlowNode>()
            val arcs = HashMap<String, Pair<TFlowNode, TFlowNode>>()
            for (jaxbElement in flowElements) {
                val element = jaxbElement.value
                if (element is TSequenceFlow) {
                    assert(element.sourceRef is TFlowNode)
                    assert(element.targetRef is TFlowNode)
                    arcs[element.id] = element.sourceRef as TFlowNode to element.targetRef as TFlowNode
                } else {
                    assert(element is TFlowNode)
                    nodes.add(element as TFlowNode)
                }
            }
            return Layouter(nodes, arcs)
        }
    }

    enum class Anchor { N, NE, E, SE, S, SW, W, NW }
    data class AbsolutePoint(var x: Int, var y: Int)
    data class AnchoredPoint<T>(val center: T, var anchor: Anchor? = null)
    data class Arc<TNode>(
        val begin: AnchoredPoint<TNode>,
        val end: AnchoredPoint<TNode>
    )

    data class Layout<TNode, TArc>(
        val nodes: Map<TNode, AbsolutePoint>,
        val arcs: Map<TArc, Arc<TNode>>
    )

    val incoming = HashMap<TNode, ArrayList<TNode>>()
    val outgoing = HashMap<TNode, ArrayList<TNode>>()

    init {
        for ((src, dst) in arcs.values) {
            incoming.computeIfAbsent(dst) { ArrayList() }.add(src)
            outgoing.computeIfAbsent(src) { ArrayList() }.add(dst)
        }
    }

    private fun computeLayers(): List<Set<TNode>> {
        val start = nodes - incoming.keys
        check(start.isNotEmpty())
        val visited = HashSet<TNode>()
        val layers = ArrayList<Set<TNode>>()
        var current: Set<TNode> = start
        while (current.isNotEmpty()) {
            // Separate elements that reference elements in current layer and put them in a sub-layer
            val (left, right) = current.partition { outgoing[it].orEmpty().any { it in current } }
            if (left.isNotEmpty()) layers.add(left.toSet())
            if (right.isNotEmpty()) layers.add(right.toSet())
            visited.addAll(current)
            current = current.flatMapTo(HashSet()) { outgoing[it].orEmpty().filter { it !in visited } }
        }
        assert(layers.flatten().size == nodes.size) { "Expected: ${nodes.size}, actual: ${layers.flatten().size}" }
        return layers
    }

    /**
     * Arranges [nodes] in the first quadrant of the 2D Cartesian coordinate system, in points with integer coordinates.
     * Arcs are represented by two points - origin and destination - enhanced with the anchor where the line should begin/end.
     */
    fun computeLayout(): Layout<TNode, TArc> {
        val layers = computeLayers()
        val positions = HashMap<TNode, AbsolutePoint>()
        for ((x, layer) in layers.withIndex()) {
            for ((y, item) in layer.withIndex()) {
                positions[item] = AbsolutePoint(x, y)
            }
        }

        val arrows = arcs.mapValues { (_, nodes) ->
            val leftNode = nodes.first
            val rightNode = nodes.second
            val left = checkNotNull(positions[leftNode])
            val right = checkNotNull(positions[rightNode])
            val lp: AnchoredPoint<TNode>
            val rp: AnchoredPoint<TNode>
            if (left.x > right.x) {
                lp = AnchoredPoint(leftNode, Anchor.W)
                rp = AnchoredPoint(rightNode, Anchor.E)
            } else {
                lp = AnchoredPoint(leftNode, Anchor.E)
                rp = AnchoredPoint(rightNode, Anchor.W)
            }
            if (left.x == right.x) {
                if (left.y < right.y) {
                    lp.anchor = Anchor.S
                    rp.anchor = Anchor.N
                } else {
                    lp.anchor = Anchor.N
                    rp.anchor = Anchor.S
                }
            }
            return@mapValues Arc(lp, rp)
        }
        return Layout(positions, arrows)
    }
}