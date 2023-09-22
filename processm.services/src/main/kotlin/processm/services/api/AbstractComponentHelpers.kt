package processm.services.api

import com.google.gson.Gson
import processm.core.helpers.mapToArray
import processm.core.helpers.toLocalDateTime
import processm.core.logging.loggedScope
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.Node
import processm.core.models.dfg.DirectlyFollowsGraph
import processm.core.models.metadata.BasicMetadata
import processm.core.models.metadata.SingleValueMetadata
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.WorkspaceComponent
import processm.dbmodels.models.load
import processm.services.api.models.*
import java.util.*

/**
 * Converts the database representation of the [WorkspaceComponent] into service API [AbstractComponent].
 */
fun WorkspaceComponent.toAbstractComponent(): AbstractComponent =
    AbstractComponent(
        id = id.value,
        query = query,
        dataStore = dataStoreId,
        type = componentType.toComponentType(),
        name = name,
        layout = getLayout(),
        customizationData = getCustomizationData(),
        data = getData(),
        dataLastModified = dataLastModified?.toLocalDateTime(),
        userLastModified = userLastModified.toLocalDateTime(),
        lastError = lastError
    )

/**
 * Converts the component type from database representation into service API [ComponentType].
 */
private fun ComponentTypeDto.toComponentType(): ComponentType = when (this) {
    ComponentTypeDto.CausalNet -> ComponentType.causalNet
    ComponentTypeDto.Kpi -> ComponentType.kpi
    ComponentTypeDto.BPMN -> ComponentType.bpmn
    ComponentTypeDto.DirectlyFollowsGraph -> ComponentType.directlyFollowsGraph
    else -> {
        val thisString = this.toString()
        requireNotNull(ComponentType.values().firstOrNull { it.toString().equals(thisString, ignoreCase = true) }) {
            "Cannot convert $this to ComponentType."
        }
    }
}

/**
 * Deserializes layout information for the component.
 */
// TODO: replace GSON with kotlinx/serialization
private fun WorkspaceComponent.getLayout(): LayoutElement? =
    if (!layoutData.isNullOrEmpty()) Gson().fromJson(layoutData, LayoutElement::class.java)
    else null


/**
 * Deserializes the customization data for the component.
 */
// TODO: replace GSON with kotlinx/serialization
private fun WorkspaceComponent.getCustomizationData(): ProcessModelCustomizationData? {
    if (customizationData.isNullOrBlank())
        return null

    return when (componentType) {
        ComponentTypeDto.CausalNet, ComponentTypeDto.PetriNet ->
            Gson().fromJson(customizationData, ProcessModelCustomizationData::class.java)

        else -> TODO("Customization data is not implemented for type $componentType.")
    }
}

/**
 * Deserializes the component data for the component.
 */
private fun WorkspaceComponent.getData(): Any? = loggedScope { logger ->
    try {
        when (componentType) {
            ComponentTypeDto.CausalNet -> {
                val cnet = DBSerializer.fetch(
                    DBCache.get(dataStoreId.toString()).database,
                    requireNotNull(data) { "Missing C-net id" }.toInt()
                )
                val nodes = ArrayList<Node>().apply {
                    add(cnet.start)
                    cnet.activities.filterTo(this) { it != cnet.start && it != cnet.end }
                    add(cnet.end)
                }.mapToArray {
                    CausalNetComponentDataAllOfNodes(
                        it.name,
                        cnet.splits[it].orEmpty().mapToArray { split -> split.targets.mapToArray { t -> t.name } },
                        cnet.joins[it].orEmpty().mapToArray { join -> join.sources.mapToArray { s -> s.name } }
                    )
                }
                val hasDependencyMeasure = BasicMetadata.DEPENDENCY_MEASURE in cnet.availableMetadata
                val edges = cnet.dependencies.mapToArray {
                    val dependencyMeasure = if (hasDependencyMeasure)
                        (cnet.getMetadata(it, BasicMetadata.DEPENDENCY_MEASURE) as SingleValueMetadata<Double>).value
                    else
                        Double.NaN
                    CausalNetComponentDataAllOfEdges(
                        it.source.name,
                        it.target.name,
                        dependencyMeasure
                    )
                }

                CausalNetComponentData(
                    type = ComponentType.causalNet,
                    nodes = nodes,
                    edges = edges
                )
            }

            ComponentTypeDto.Kpi -> {
                KpiComponentData(
                    type = ComponentType.kpi,
                    value = data
                )
            }

            ComponentTypeDto.BPMN -> {
                BPMNComponentData(
                    type = ComponentType.bpmn,
                    xml = javaClass.classLoader.getResourceAsStream("bpmn-mock/pizza-collaboration.bpmn")
                        .bufferedReader().readText() // FIXME: replace the mock with actual implementation
                )
            }

            ComponentTypeDto.PetriNet -> {
                val petriNet = processm.core.models.petrinet.DBSerializer.fetch(
                    DBCache.get(dataStoreId.toString()).database,
                    UUID.fromString(requireNotNull(data) { "Missing PetriNet id" })
                )
                val componentDataTransitions = petriNet.transitions.mapToArray {
                    PetriNetComponentDataAllOfTransitions(
                        it.id.toString(),
                        it.name,
                        it.isSilent,
                        it.inPlaces.mapToArray { it.id.toString() },
                        it.outPlaces.mapToArray { it.id.toString() }
                    )
                }

                PetriNetComponentData(
                    type = ComponentType.petriNet,
                    initialMarking = petriNet.initialMarking.mapKeys { it.key.id.toString() },
                    finalMarking = petriNet.finalMarking.mapKeys { it.key.id.toString() },
                    places = petriNet.places.mapToArray { PetriNetComponentDataAllOfPlaces(it.id.toString()) },
                    transitions = componentDataTransitions
                )
            }

            ComponentTypeDto.DirectlyFollowsGraph -> {
                val dfg = DirectlyFollowsGraph.load(
                    DBCache.get(dataStoreId.toString()).database,
                    UUID.fromString(requireNotNull(data) { "Missing DFG id" })
                )

                DirectlyFollowsGraphComponentData(
                    type = ComponentType.directlyFollowsGraph,
                    nodes = dfg.activities.mapToArray {
                        DFGNode(
                            it.name,
                            it.name
                        )
                    },
                    edges = dfg.graph.rows.flatMap { source ->
                        dfg.graph.getRow(source).map { (target, arc) ->
                            DFGEdge(
                                id = "${source.name}->${target.name}",
                                source = source.name,
                                target = target.name,
                                label = arc.cardinality.toString(),
                                support = arc.cardinality.toDouble()
                            )
                        }
                    }.toTypedArray()
                )
            }

            ComponentTypeDto.TreeLogView -> {
                null
            }

            ComponentTypeDto.FlatLogView -> {
                null
            }

            else -> TODO("Data conversion is not implemented for type $componentType.")
        }
    } catch (e: Throwable) {
        logger.warn(e.message, e)
        null
    }
}


private fun PetriNetComponentData.toPetriNet(): PetriNet {
    val places = this.places.associate { it.id to Place(UUID.fromString(it.id)) }
    val transitions = this.transitions.map {
        val inPlaces = it.inPlaces.map(places::getValue)
        val outPlaces = it.outPlaces.map(places::getValue)
        Transition(
            name = it.name,
            inPlaces = inPlaces,
            outPlaces = outPlaces,
            isSilent = it.isSilent,
            id = UUID.fromString(it.id)
        )
    }
    val initialMarking = Marking(this.initialMarking.mapKeys { places.getValue(it.key) })
    val finalMarking = Marking(this.finalMarking.mapKeys { places.getValue(it.key) })
    return PetriNet(places.values.toList(), transitions, initialMarking, finalMarking)
}

/**
 * Updates the data within the component from the JSON received in the abstract component from the frontend
 */
fun WorkspaceComponent.updateData(data: String) = loggedScope { logger ->
    when (componentType) {
        ComponentTypeDto.PetriNet -> {
            // TODO: replace GSON with kotlinx/serialization
            val petriNet = Gson().fromJson(data, PetriNetComponentData::class.java).toPetriNet()
            processm.core.models.petrinet.DBSerializer.update(
                DBCache.get(dataStoreId.toString()).database,
                UUID.fromString(this.data),
                petriNet
            )
        }

        else -> logger.error("Updating data for $componentType is currently not supported")
    }
}
