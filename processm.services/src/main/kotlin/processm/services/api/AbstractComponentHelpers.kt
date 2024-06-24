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
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import processm.enhancement.kpi.Report
import processm.helpers.mapToArray
import processm.helpers.time.toLocalDateTime
import processm.logging.loggedScope
import processm.miners.ALGORITHM_HEURISTIC_MINER
import processm.miners.ALGORITHM_INDUCTIVE_MINER
import processm.services.JsonSerializer
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

fun ProcessModelComponentData.retrievePetriNetComponentData(modelVersion: Long?): PetriNetComponentData? =
    loggedScope { logger ->
        val actualModelVersion = (modelVersion ?: acceptedModelVersion) ?: return null.apply {
            logger.warn("Missing Petri-net id for component ${component.id}.")
        }
        val petriNet = models[actualModelVersion]?.let {
            processm.core.models.petrinet.DBSerializer.fetch(
                DBCache.get(component.dataStoreId.toString()).database,
                UUID.fromString(it)
            )
        } ?: return null.apply {
            logger.warn("Missing Petri-net id for component ${component.id}.")
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

        val alignmentKPIReport = getMostRecentAlignmentKPIReport()?.let { reportURI ->
            DurablePersistenceProvider(component.dataStoreId.toString()).use { it.get<Report>(reportURI) }
        }
        PetriNetComponentData(
            type = ComponentType.petriNet,
            initialMarking = HashMap<String, Int>().apply {
                for ((p, t) in petriNet.initialMarking)
                    put(p.id.toString(), t.size)
            },
            finalMarking = HashMap<String, Int>().apply {
                for ((p, t) in petriNet.finalMarking)
                    put(p.id.toString(), t.size)
            },
            places = petriNet.places.mapToArray { PetriNetComponentDataAllOfPlaces(it.id.toString()) },
            transitions = componentDataTransitions,
            alignmentKPIReport = alignmentKPIReport,
            modelVersion = actualModelVersion,
            newestVersion = this.models.keys.maxOrNull()
        )
    }

fun ProcessModelComponentData.retrieveCausalNetComponentData(modelVersion: Long?): CausalNetComponentData? =
    loggedScope { logger ->
        val actualModelVersion = (modelVersion ?: acceptedModelVersion) ?: return null.apply {
            logger.warn("No model available")
        }
        val cnet = models[actualModelVersion]?.let {
            DBSerializer.fetch(
                DBCache.get(component.dataStoreId.toString()).database,
                it.toInt()
            )
        } ?: return null.apply {
            logger.warn("Missing C-net id for component ${component.id}.")
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
        val alignmentKPIReport = getMostRecentAlignmentKPIReport(actualModelVersion)?.let { reportURI ->
            DurablePersistenceProvider(component.dataStoreId.toString()).use { it.get<Report>(reportURI) }
        }
        return@loggedScope CausalNetComponentData(
            type = ComponentType.causalNet,
            nodes = nodes,
            edges = edges,
            alignmentKPIReport = alignmentKPIReport,
            modelVersion = actualModelVersion,
            newestVersion = this.models.keys.maxOrNull()
        )
    }

/**
 * Deserializes the component data for the component.
 */
private fun WorkspaceComponent.getData(): Any? = loggedScope { logger ->
    try {
        when (componentType) {
            ComponentTypeDto.CausalNet -> {
                ProcessModelComponentData.create(this).retrieveCausalNetComponentData(null)
            }

            ComponentTypeDto.Kpi -> {
                KpiComponentData(
                    type = ComponentType.kpi,
                    value = data
                )
            }

            ComponentTypeDto.BPMN -> {
                val recentData = ProcessModelComponentData.create(this).acceptedModelId
                    ?: return null.apply { logger.warn("Missing BMPN id for component $id.") }

                BPMNComponentData(
                    type = ComponentType.bpmn,
                    xml = processm.core.models.bpmn.DBSerializer.fetchXML(
                        DBCache.get(dataStoreId.toString()).database,
                        UUID.fromString(recentData)
                    ) // TODO: add KPIs/alignments
                )
            }

            ComponentTypeDto.PetriNet -> {
                ProcessModelComponentData.create(this).retrievePetriNetComponentData(null)
            }

            ComponentTypeDto.DirectlyFollowsGraph -> {
                val recentData = ProcessModelComponentData.create(this)
                val dfg = recentData.acceptedModelId?.let {
                    DirectlyFollowsGraph.load(
                        DBCache.get(dataStoreId.toString()).database,
                        UUID.fromString(it)
                    )
                } ?: return null.apply {
                    logger.warn("Missing DFG id for component $id.")
                }

                assert(recentData.alignmentKPIReports.isEmpty()) { "DFG does not have executable semantics; got alignment KPI report for component $id" }

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
        ComponentTypeDto.CausalNet -> {
            JsonSerializer.decodeFromString<CausalNetComponentData>(data).modelVersion?.let { modelVersion ->
                val componentData = ProcessModelComponentData.create(this)
                if (componentData.acceptedModelVersion != modelVersion) {
                    this.data = componentData
                        .apply { acceptedModelVersion = modelVersion }
                        .toJSON()
                    afterCommit {
                        this as WorkspaceComponent
                        triggerEvent(event = WorkspaceComponentEventType.ModelAccepted)
                    }
                }
            }
        }

        ComponentTypeDto.PetriNet -> {
            JsonSerializer.decodeFromString<PetriNetComponentData>(data).modelVersion?.let { modelVersion ->
                val petriNet = JsonSerializer.decodeFromString<PetriNetComponentData>(data).toPetriNet()
                val componentData = ProcessModelComponentData.create(this)
                if (componentData.acceptedModelVersion != modelVersion) {
                    this.data = componentData
                        .apply { acceptedModelVersion = modelVersion }
                        .toJSON()
                    afterCommit {
                        this as WorkspaceComponent
                        triggerEvent(event = WorkspaceComponentEventType.ModelAccepted)
                    }
                }
                processm.core.models.petrinet.DBSerializer.update(
                    DBCache.get(dataStoreId.toString()).database,
                    UUID.fromString(componentData.acceptedModelId ?: error("Model ID is not set for component $id")),
                    petriNet
                )
            }
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
fun WorkspaceComponent.getCustomProperties() = getCustomProperties(componentType, properties)

/**
 * Gets the list of custom properties for the component of the [componentType] type
 */
fun getCustomProperties(
    componentType: ComponentTypeDto,
    properties: Map<String, String>? = null
): Array<CustomProperty> =
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
                    value = properties?.get("algorithm") ?: ALGORITHM_HEURISTIC_MINER
                ),
                CustomProperty(
                    id = 1,
                    name = "horizon",
                    type = "non-negative-integer",
                    value = properties?.get("horizon") ?: "3"
                )
            )

        else -> emptyArray()
    }

