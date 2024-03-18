package processm.core.models.bpmn.converters

import jakarta.xml.bind.JAXBElement
import kotlinx.serialization.json.*
import processm.core.models.bpmn.BPMNModel
import processm.core.models.bpmn.jaxb.*
import processm.logging.logger
import javax.xml.namespace.QName
import kotlin.math.ceil

abstract class ToBPMN {


    companion object {
        /**
         * Margin between the border of the plane and the content
         */
        const val MARGIN = 50.0

        /**
         * The horizontal distance between consecutive layers of the graph in the diagram
         */
        const val HORIZONTAL_DISTANCE = 100.0

        /**
         * The vertical distance between neighboring shapes within the same layer of the diagram
         */
        const val VERTICAL_DISTANCE = 50.0

        /**
         * The width of a shape in the diagram
         */
        const val WIDTH = 30.0

        /**
         * The height of a shape in the diagram
         */
        const val HEIGHT = 30.0

        // I have absolute no idea what unit are these in
        // TODO some doc
        const val LINE_HEIGHT = 16
        const val CHARACTER_WIDTH = 8
        const val MAX_CHARACTERS_PER_LINE = 8
        const val MIN_HEIGHT = LINE_HEIGHT
        const val MAX_WIDTH = CHARACTER_WIDTH * MAX_CHARACTERS_PER_LINE

        val logger = logger()
    }

    protected val factory = ObjectFactory()
    protected val flowElements = ArrayList<JAXBElement<out TFlowElement>>()


    private inline fun fillId(n: TFlowElement) {
        if (n.id == null)
            n.id = "id_${flowElements.size}"
    }

    protected fun add(n: TStartEvent): TStartEvent {
        fillId(n)
        flowElements.add(factory.createStartEvent(n))
        return n
    }

    protected fun add(n: TEndEvent): TEndEvent {
        fillId(n)
        flowElements.add(factory.createEndEvent(n))
        return n
    }

    protected fun add(n: TTask): TTask {
        fillId(n)
        flowElements.add(factory.createTask(n))
        return n
    }

    protected fun add(n: TExclusiveGateway): TExclusiveGateway {
        fillId(n)
        flowElements.add(factory.createExclusiveGateway(n))
        return n
    }

    protected fun add(n: TParallelGateway): TParallelGateway {
        fillId(n)
        flowElements.add(factory.createParallelGateway(n))
        return n
    }

    protected fun add(n: TSequenceFlow): TSequenceFlow {
        fillId(n)
        flowElements.add(factory.createSequenceFlow(n))
        return n
    }

    protected fun link(src: TFlowNode, dst: TFlowNode) {
        require(src !== dst)
        val link = TSequenceFlow()
        link.sourceRef = src
        link.targetRef = dst
        add(link)
        src.outgoing.add(QName(link.id))
        dst.incoming.add(QName(link.id))
    }

    private val bounds = HashMap<TFlowNode, Bounds>()

    private fun Layouter.AnchoredPoint<TFlowNode>.toWaypoint(): Point {
        val d = bounds.getValue(this.center)
        var x = d.x
        var y = d.y
        val width = d.width
        val height = d.height
        when (anchor) {
            Layouter.Anchor.N -> {
                x += width / 2
            }

            Layouter.Anchor.NE -> {
                x += width
            }

            Layouter.Anchor.E -> {
                x += width
                y += height / 2
            }

            Layouter.Anchor.SE -> {
                x += width
                y += height
            }

            Layouter.Anchor.S -> {
                x += width / 2
                y += height
            }

            Layouter.Anchor.SW -> {
                y += height
            }

            Layouter.Anchor.W -> {
                y += height / 2
            }

            Layouter.Anchor.NW -> {}    // do nothing
            null -> {
                x += width / 2
                y += height / 2
            }
        }
        return Point().apply {
            this.x = x
            this.y = y
        }
    }

    private fun toWaypoints(arc: Layouter.Arc<TFlowNode>, waypoints: MutableList<Point>) {
        waypoints.add(arc.begin.toWaypoint())
        waypoints.add(arc.end.toWaypoint())
    }

    private fun Layouter.Layout<TFlowNode, String>.computeDimensions() = nodes.keys.associateWith {
        if (it.name.isNullOrBlank()) return@associateWith null
        val n = it.name?.length ?: return@associateWith null
        return@associateWith if (n < MAX_CHARACTERS_PER_LINE) {
            CHARACTER_WIDTH * n to MIN_HEIGHT
        } else {
            val nLines = ceil(n / MAX_CHARACTERS_PER_LINE.toDouble()).toInt()
            MAX_WIDTH to nLines * LINE_HEIGHT
        }
    }


    private fun Layouter.Layout<TFlowNode, String>.computeBounds(
        dimensions: Map<TFlowNode, Pair<Int, Int>?>
    ) {
        bounds.clear()
        var x = MARGIN
        var y = MARGIN
        var maxWidth = 0.0
        var previous: Layouter.AbsolutePoint? = null
        for ((k, v) in nodes.entries.sortedWith { a, b ->
            if (a.value.x != b.value.x) a.value.x - b.value.x
            else a.value.y - b.value.y
        }) {
            if (previous !== null) {
                if (previous.x != v.x) {
                    // next column
                    x += maxWidth + HORIZONTAL_DISTANCE
                    y = MARGIN
                    maxWidth = 0.0
                } else {
                    y += VERTICAL_DISTANCE
                }
            }
            bounds[k] = Bounds().apply {
                this.x = x
                this.y = y
                val d = dimensions[k]
                this.width = d?.first?.toDouble() ?: WIDTH
                this.height = d?.second?.toDouble() ?: HEIGHT
                maxWidth = maxWidth.coerceAtLeast(this.width)
                y += this.height
            }
            previous = v
        }
    }


    protected fun createDiagram(collaborationId: QName, participantId: QName): BPMNDiagram =
        factory.createBPMNDiagram().apply {
            try {
                bpmnPlane = factory.createBPMNPlane().apply {
                    this.bpmnElement = collaborationId
                    fillDot(participantId)
                }
            } catch (e: Exception) {
                logger.warn("Cannot create the layout using Graphviz", e)
                bpmnPlane = factory.createBPMNPlane().apply {
                    this.bpmnElement = collaborationId
                    fillNaive(participantId)
                }
            }
        }

    private fun BPMNPlane.fillNaive(participantId: QName) {
        val layout = Layouter.fromFlowElements(flowElements).computeLayout()

        val dimensions = layout.computeDimensions()
        layout.computeBounds(dimensions)
        this.diagramElement.add(factory.createBPMNShape(BPMNShape().apply {
            bpmnElement = participantId
            isIsHorizontal = true
            this.bounds = factory.createBounds().apply {
                x = 0.0
                y = 0.0
                width = this@ToBPMN.bounds.values.maxOf { it.x + it.width } + 2 * MARGIN
                height = this@ToBPMN.bounds.values.maxOf { it.y + it.height } + 2 * MARGIN
            }
        }))
        bounds.mapTo(this.diagramElement) { (node, bounds) ->
            factory.createBPMNShape(BPMNShape().apply {
                bpmnElement = QName(node.id)
                this.bounds = bounds
            })
        }

        layout.arcs.mapTo(this.diagramElement) { (id, points) ->
            factory.createBPMNEdge(BPMNEdge().apply {
                bpmnElement = QName(id)
                toWaypoints(points, waypoint)
            })
        }
    }

    protected fun finish(): BPMNModel {
        val process = TProcess().apply {
            flowElement.addAll(flowElements)
            id = "process1"
        }
        val model = TDefinitions().apply {
            targetNamespace = targetNamespace
            rootElement.add(factory.createProcess(process))
            val collaborationId = "collaboration1"
            rootElement.add(factory.createCollaboration(TCollaboration().apply {
                this.id = collaborationId
                this.participant.add(factory.createTParticipant().apply {
                    this.processRef = QName(process.id)
                    this.id = "participant1"
                })
            }))
            bpmnDiagram.add(createDiagram(QName(collaborationId), QName("participant1")))
        }
        return BPMNModel(model)
    }

    private fun boundsFromObject(element: JsonObject): Bounds {
        val points = checkNotNull(element.jsonObject["_draw_"]?.jsonArray?.first {
            it.jsonObject["op"]?.jsonPrimitive?.content.equals("p", true)
        }?.jsonObject?.get("points")?.jsonArray)
        assert(points.size == 4)
        val minX = points.minOf { it.jsonArray[0].jsonPrimitive.double }
        val minY = points.minOf { it.jsonArray[1].jsonPrimitive.double }
        val maxX = points.maxOf { it.jsonArray[0].jsonPrimitive.double }
        val maxY = points.maxOf { it.jsonArray[1].jsonPrimitive.double }
        return factory.createBounds().apply {
            x = minX
            y = minY
            width = maxX - minX
            height = maxY - minY
        }
    }

    private fun BPMNPlane.fillDot(participantId: QName) {
        val layout = runDot()
        this.diagramElement.add(factory.createBPMNShape(BPMNShape().apply {
            bpmnElement = participantId
            isIsHorizontal = true
            this.bounds = boundsFromObject(layout)
            with(this.bounds) {
                x -= MARGIN
                y -= MARGIN
                width += 2 * MARGIN
                height += 2 * MARGIN
            }
        }))
        checkNotNull(layout.jsonObject["objects"]?.jsonArray).mapTo(this.diagramElement) { graphNode ->
            factory.createBPMNShape(BPMNShape().apply {
                bpmnElement = QName(checkNotNull(graphNode.jsonObject["name"]?.jsonPrimitive?.content))
                this.bounds = boundsFromObject(graphNode.jsonObject)
            })
        }
        checkNotNull(layout.jsonObject["edges"]?.jsonArray).mapTo(this.diagramElement) { graphEdge ->
            factory.createBPMNEdge(BPMNEdge().apply {
                bpmnElement = QName(checkNotNull(graphEdge.jsonObject["name"]?.jsonPrimitive?.content))
                graphEdge.jsonObject["_draw_"]?.jsonArray
                    ?.firstOrNull { it.jsonObject["op"]?.jsonPrimitive?.content.equals("b", true) }
                    ?.jsonObject?.get("points")?.jsonArray
                    ?.mapTo(waypoint) {
                        Point().apply {
                            this.x = it.jsonArray[0].jsonPrimitive.double
                            this.y = it.jsonArray[1].jsonPrimitive.double
                        }
                    }
            })
        }
    }

    private fun runDot(): JsonObject {
        val nodes = flowElements.fold(ArrayList<TFlowNode>()) { target, element ->
            (element.value as? TFlowNode)?.let { target.add(it) }
            target
        }
        val arcs = flowElements.fold(ArrayList<TSequenceFlow>()) { target, element ->
            (element.value as? TSequenceFlow)?.let { target.add(it) }
            target
        }
        val dotCode = buildString {
            append("graph test {\nrankdir=LR\nsplines=polyline\n")
            for (node in nodes) {
                append(node.id)
                append(" [shape=")
                if (node is TGateway)
                    append("diamond, regular=true")
                else
                    append("box")
                if (!node.name.isNullOrBlank()) {
                    append(", label=\"")
                    append(node.name)
                    append("\"")
                }
                append("];\n")
            }
            for (element in arcs) {
                val src = element.sourceRef as TFlowNode
                val dst = element.targetRef as TFlowNode
                append(src.id)
                append(" -- ")
                append(dst.id)
                append(" [name=\"")
                append(element.id)
                append("\"];\n")
            }
            append("}")
        }
        val element = with(ProcessBuilder("dot", "-Tjson").start()) {
            outputStream.write(dotCode.encodeToByteArray())
            outputStream.close()
            val json = inputStream.readAllBytes().decodeToString()
            waitFor()
            Json.parseToJsonElement(json)
        }
        return element as JsonObject
    }

}