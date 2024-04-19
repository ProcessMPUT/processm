package processm.services.api

import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.Node
import processm.core.models.dfg.DirectlyFollowsGraph
import processm.core.models.metadata.BasicMetadata
import processm.core.models.metadata.SingleDoubleMetadata
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import processm.core.persistence.DurablePersistenceProvider
import processm.core.persistence.connection.DBCache
import processm.core.persistence.get
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.WorkspaceComponent
import processm.dbmodels.models.dataAsObject
import processm.dbmodels.models.load
import processm.dbmodels.models.mostRecentData
import processm.enhancement.kpi.Report
import processm.helpers.mapToArray
import processm.helpers.time.toLocalDateTime
import processm.logging.loggedScope
import processm.miners.ALGORITHM_HEURISTIC_MINER
import processm.miners.ALGORITHM_INDUCTIVE_MINER
import processm.services.JsonSerializer
import processm.services.api.models.*
import java.net.URI
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
        lastError = lastError,
        customProperties = getCustomProperties()
    )

/**
 * Converts the component type from database representation into service API [ComponentType].
 */
fun ComponentTypeDto.toComponentType(): ComponentType = when (this) {
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
private fun WorkspaceComponent.getLayout(): LayoutElement? =
    if (!layoutData.isNullOrEmpty()) JsonSerializer.decodeFromString<LayoutElement>(layoutData!!)
    else null


/**
 * Deserializes the customization data for the component.
 */
private fun WorkspaceComponent.getCustomizationData(): CustomizationData? {
    if (customizationData.isNullOrBlank())
        return null

    return when (componentType) {
        ComponentTypeDto.CausalNet, ComponentTypeDto.PetriNet ->
            customizationData?.let { JsonSerializer.decodeFromString<CustomizationData>(it) }

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
                val cnet = mostRecentData()?.let {
                    DBSerializer.fetch(
                        DBCache.get(dataStoreId.toString()).database,
                        it.toInt()
                    )
                } ?: return null.apply {
                    logger.warn("Missing C-net id for component $id.")
                }
                val nodes = ArrayList<Node>().apply {
                    add(cnet.start)
                    cnet.activities.filterTo(this) { it != cnet.start && it != cnet.end }
                    add(cnet.end)
                }.mapToArray {
                    CausalNetComponentDataAllOfNodes(
                        id = "${it.name}${it.instanceId}",
                        name = it.name,
                        splits = cnet.splits[it].orEmpty().mapToArray { spl -> spl.targets.mapToArray { t -> t.name } },
                        joins = cnet.joins[it].orEmpty().mapToArray { join -> join.sources.mapToArray { s -> s.name } }
                    )
                }
                val edges = cnet.dependencies.mapToArray {
                    val dependencyMeasure =
                        (cnet.getAllMetadata(it)[BasicMetadata.DEPENDENCY_MEASURE] as SingleDoubleMetadata?)?.value
                            ?: 0.0
                    CausalNetComponentDataAllOfEdges(
                        sourceNodeId = "${it.source.name}${it.source.instanceId}",
                        targetNodeId = "${it.target.name}${it.target.instanceId}",
                        support = dependencyMeasure
                    )
                }

                val alignmentKPIReport = if (cnet.alignmentKPIId.isNotBlank())
                    DurablePersistenceProvider(dataStoreId.toString()).use { it.get<Report>(URI(recentData.alignmentKPIId)) }
                else null
                CausalNetComponentData(
                    type = ComponentType.causalNet,
                    nodes = nodes,
                    edges = edges,
                    alignmentKPIReport = alignmentKPIReport
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
                    xml = processm.core.models.bpmn.DBSerializer.fetchXML(
                        DBCache.get(dataStoreId.toString()).database,
                        UUID.fromString(requireNotNull(mostRecentData()) { "Missing BPMN model id" })
                    )
                )
            }

            ComponentTypeDto.PetriNet -> {
                val recentData = mostRecentData() // dataAsObject?
                val petriNet = recentData?.modelId?.let {
                    processm.core.models.petrinet.DBSerializer.fetch(
                        DBCache.get(dataStoreId.toString()).database,
                        UUID.fromString(it)
                    )
                } ?: return null.apply {
                    logger.warn("Missing Petri-net id for component $id.")
                }

                val componentDataTransitions = petriNet.transitions.mapToArray {
                    PetriNetComponentDataAllOfTransitions(
                        it.id.toString(),
                        it.name,
                        it.isSilent,
                        it.inPlaces.mapToArray { it.id.toString() },
                        it.outPlaces.mapToArray { it.id.toString() }
                    )
                }

                val alignmentKPIReport = if (recentData.alignmentKPIId.isNotBlank())
                    DurablePersistenceProvider(dataStoreId.toString()).use { it.get<Report>(URI(recentData.alignmentKPIId)) }
                else null
                PetriNetComponentData(
                    type = ComponentType.petriNet,
                    initialMarking = HashMap<String, Int>().apply {
                        for ((p, t) in petriNet.initialMarking) put(
                            p.toString(),
                            t.size
                        )
                    },
                    finalMarking = HashMap<String, Int>().apply {
                        for ((p, t) in petriNet.finalMarking) put(
                            p.toString(),
                            t.size
                        )
                    },
                    places = petriNet.places.mapToArray { PetriNetComponentDataAllOfPlaces(it.id.toString()) },
                    transitions = componentDataTransitions,
                    alignmentKPIReport = alignmentKPIReport
                )
            }

            ComponentTypeDto.DirectlyFollowsGraph -> {
                val recentData = mostRecentData()
                val dfg = recentData?.modelId?.let {
                    DirectlyFollowsGraph.load(
                        DBCache.get(dataStoreId.toString()).database,
                        UUID.fromString(it)
                    )
                } ?: return null.apply {
                    logger.warn("Missing DFG id for component $id.")
                }

                assert(recentData.alignmentKPIId.isEmpty()) { "DFG does not have executable semantics" }

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

            ComponentTypeDto.AlignerKpi -> {
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
            val petriNet = JsonSerializer.decodeFromString<PetriNetComponentData>(data).toPetriNet()
            processm.core.models.petrinet.DBSerializer.update(
                DBCache.get(dataStoreId.toString()).database,
                UUID.fromString(this.data),
                petriNet
            )
        }

        ComponentTypeDto.BPMN -> {
            JsonSerializer.decodeFromString<BPMNComponentData>(data).xml?.let { xml ->
                processm.core.models.bpmn.DBSerializer.update(
                    DBCache.get(dataStoreId.toString()).database,
                    UUID.fromString(this.data),
                    xml
                )
            }
        }

        else -> logger.error("Updating data for $componentType is currently not supported")
    }
}

/**
 * Gets the list of custom properties for this component.
 */
fun WorkspaceComponent.getCustomProperties() = getCustomProperties(componentType, algorithm)

/**
 * Gets the list of custom properties for the component of the [componentType] type
 */
fun getCustomProperties(componentType: ComponentTypeDto, algorithm: String? = null): Array<CustomProperty> =
    when (componentType) {
        ComponentTypeDto.CausalNet, ComponentTypeDto.BPMN, ComponentTypeDto.PetriNet ->
            arrayOf(
                CustomProperty(
                    id = 0,
                    name = "algorithm",
                    type = "enum",
                    enum = arrayOf(
                        EnumItem(
                            id = ALGORITHM_HEURISTIC_MINER,
                            name = "Online Heuristic Miner"
                        ),
                        EnumItem(
                            id = ALGORITHM_INDUCTIVE_MINER,
                            name = "Online Inductive Miner"
                        )
                    ),
                    value = algorithm ?: ALGORITHM_HEURISTIC_MINER
                )
            )

        else -> emptyArray()
    }

