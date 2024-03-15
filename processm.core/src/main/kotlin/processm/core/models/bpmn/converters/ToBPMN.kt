package processm.core.models.bpmn.converters

import jakarta.xml.bind.JAXBElement
import processm.core.models.bpmn.BPMNModel
import processm.core.models.bpmn.jaxb.*
import javax.xml.namespace.QName

abstract class ToBPMN {


    companion object {
        /**
         * Margin between the border of the plane and the content
         */
        const val MARGIN = 50.0

        /**
         * The horizontal distance between consecutive layers of the graph in the diagram
         */
        const val X_MULTIPLIER = 100.0

        /**
         * The vertical distance between neighboring shapes within the same layer of the diagram
         */
        const val Y_MULTIPLIER = 50.0

        /**
         * The width of a shape in the diagram
         */
        const val WIDTH = 30.0

        /**
         * The height of a shape in the diagram
         */
        const val HEIGHT = 30.0
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


    protected fun createDiagram(collaborationId: QName, participantId: QName): BPMNDiagram =
        factory.createBPMNDiagram().apply {
            bpmnPlane = factory.createBPMNPlane().apply {
                this.bpmnElement = collaborationId
                val layout = Layouter.fromFlowElements(flowElements).computeLayout()
                this.diagramElement.add(factory.createBPMNShape(BPMNShape().apply {
                    bpmnElement = participantId
                    isIsHorizontal = true
                    bounds = factory.createBounds().apply {
                        x = 0.0
                        y = 0.0
                        width = layout.nodes.values.maxOf { it.x } * X_MULTIPLIER + 2 * MARGIN
                        height = layout.nodes.values.maxOf { it.y } * Y_MULTIPLIER + 2 * MARGIN
                    }
                }))
                layout.nodes.mapTo(this.diagramElement) { (node, position) ->
                    factory.createBPMNShape(BPMNShape().apply {
                        bpmnElement = QName(node.id)
                        bounds = factory.createBounds().apply {
                            x = position.x * X_MULTIPLIER + MARGIN
                            y = position.y * Y_MULTIPLIER + MARGIN
                            width = WIDTH
                            height = HEIGHT
                        }
                    })
                }
                layout.arcs.mapTo(this.diagramElement) { (id, points) ->
                    factory.createBPMNEdge(BPMNEdge().apply {
                        bpmnElement = QName(id)
                        points.mapTo(waypoint) { point ->
                            Point().apply {
                                x = point.x * X_MULTIPLIER + MARGIN
                                y = point.y * Y_MULTIPLIER + MARGIN
                                when (point.anchor) {
                                    Layouter.Anchor.N -> {
                                        x += WIDTH / 2
                                    }

                                    Layouter.Anchor.NE -> {
                                        x += WIDTH
                                    }

                                    Layouter.Anchor.E -> {
                                        x += WIDTH
                                        y += HEIGHT / 2
                                    }

                                    Layouter.Anchor.SE -> {
                                        x += WIDTH
                                        y += HEIGHT
                                    }

                                    Layouter.Anchor.S -> {
                                        x += WIDTH / 2
                                        y += HEIGHT
                                    }

                                    Layouter.Anchor.SW -> {
                                        y += HEIGHT
                                    }

                                    Layouter.Anchor.W -> {
                                        y += HEIGHT / 2
                                    }

                                    Layouter.Anchor.NW -> {}    // do nothing
                                    null -> {
                                        x += WIDTH / 2
                                        y += HEIGHT / 2
                                    }
                                }
                            }
                        }
                    })
                }
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

}