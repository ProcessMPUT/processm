package processm.services.api

import com.google.gson.Gson
import processm.core.helpers.mapToArray
import processm.core.logging.loggedScope
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.Node
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.WorkspaceComponent
import processm.services.api.models.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Converts an [Instant] to [LocalDateTime] in a uniform way.
 */
fun Instant.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneId.of("Z")).withNano(0)

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
private fun WorkspaceComponent.getCustomizationData(): CausalNetComponentAllOfCustomizationData? {
    if (customizationData.isNullOrBlank())
        return null

    return when (componentType) {
        ComponentTypeDto.CausalNet ->
            Gson().fromJson(customizationData, CausalNetComponentAllOfCustomizationData::class.java)
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
                val edges = cnet.dependencies.mapToArray {
                    CausalNetComponentDataAllOfEdges(
                        it.source.name,
                        it.target.name
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
            else -> TODO("Data conversion is not implemented for type $componentType.")
        }
    } catch (e: Throwable) {
        logger.warn(e.message, e)
        null
    }
}

